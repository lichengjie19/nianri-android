package com.nianri.app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class NearbyTransferProtocol {
    private static final int MAGIC = 0x4E525431; // NRT1
    private static final int ACK_MAGIC = 0x4E524131; // NRA1
    private static final int VERSION = 1;
    private static final int ACK_OK = 0;
    private static final int ACK_INVALID_SESSION = 1;
    private static final int ACK_REJECTED = 2;
    private static final int MAX_ENCRYPTED_BYTES = TransferData.MAX_PAYLOAD_BYTES + 32;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SOCKET_TIMEOUT_MS = 20_000;
    private static final long DEFAULT_RECEIVE_WINDOW_MS = 5 * 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] AAD_PREFIX = "nianri-transfer-v1".getBytes(StandardCharsets.UTF_8);

    interface ReceiverCallback {
        boolean onPayload(byte[] payload);

        void onFailure(String message);
    }

    static final class SendResult {
        final boolean success;
        final String message;

        private SendResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static SendResult success() {
            return new SendResult(true, "新手机已安全收到数据");
        }

        static SendResult failure(String message) {
            return new SendResult(false, message);
        }
    }

    static final class PairingInfo {
        final String host;
        final int port;
        final byte[] sessionId;
        final byte[] secret;

        PairingInfo(String host, int port, byte[] sessionId, byte[] secret) {
            this.host = host;
            this.port = port;
            this.sessionId = sessionId.clone();
            this.secret = secret.clone();
        }

        String encode() {
            String encodedHost = encodeBase64(host.getBytes(StandardCharsets.UTF_8));
            return "nianri://transfer/pair?v=" + VERSION
                    + "&h=" + encodedHost
                    + "&p=" + port
                    + "&s=" + encodeBase64(sessionId)
                    + "&k=" + encodeBase64(secret);
        }

        String safetyCode() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(sessionId);
                byte[] value = digest.digest(secret);
                int number = ((value[0] & 0xff) << 16)
                        | ((value[1] & 0xff) << 8)
                        | (value[2] & 0xff);
                return String.format(Locale.ROOT, "%06d", number % 1_000_000);
            } catch (GeneralSecurityException error) {
                throw new IllegalStateException("无法生成安全码", error);
            }
        }

        static PairingInfo parse(String raw) {
            try {
                URI uri = URI.create(raw == null ? "" : raw.trim());
                if (!"nianri".equalsIgnoreCase(uri.getScheme())
                        || !"transfer".equalsIgnoreCase(uri.getHost())
                        || !"/pair".equals(uri.getPath())) {
                    throw new IllegalArgumentException("这不是念日换机二维码");
                }
                Map<String, String> query = parseQuery(uri.getRawQuery());
                if (Integer.parseInt(required(query, "v")) != VERSION) {
                    throw new IllegalArgumentException("二维码版本不兼容，请更新两台手机上的念日");
                }
                String host = new String(
                        decodeBase64(required(query, "h")),
                        StandardCharsets.UTF_8
                );
                int port = Integer.parseInt(required(query, "p"));
                byte[] session = decodeBase64(required(query, "s"));
                byte[] secret = decodeBase64(required(query, "k"));
                if (!isAllowedLanAddress(host) || port < 1 || port > 65_535
                        || session.length != 8 || secret.length != 32) {
                    throw new IllegalArgumentException("二维码中的连接信息无效");
                }
                return new PairingInfo(host, port, session, secret);
            } catch (IllegalArgumentException error) {
                if (error.getMessage() != null && !error.getMessage().isEmpty()) throw error;
                throw new IllegalArgumentException("二维码内容无效", error);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("二维码内容无效", error);
            }
        }
    }

    static final class ReceiverSession implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final PairingInfo pairingInfo;
        private final long receiveWindowMs;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private volatile boolean closed;

        private ReceiverSession(
                ServerSocket serverSocket,
                PairingInfo pairingInfo,
                long receiveWindowMs
        ) {
            this.serverSocket = serverSocket;
            this.pairingInfo = pairingInfo;
            this.receiveWindowMs = receiveWindowMs;
        }

        PairingInfo pairingInfo() {
            return pairingInfo;
        }

        void start(ReceiverCallback callback) {
            if (!started.compareAndSet(false, true)) {
                throw new IllegalStateException("接收会话已经开始");
            }
            Thread thread = new Thread(() -> receiveLoop(callback), "nianri-transfer-receiver");
            thread.start();
        }

        private void receiveLoop(ReceiverCallback callback) {
            long deadline = System.currentTimeMillis() + receiveWindowMs;
            try {
                serverSocket.setSoTimeout(1_000);
                while (!closed && System.currentTimeMillis() < deadline) {
                    try (Socket socket = serverSocket.accept()) {
                        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                        if (handleConnection(socket, callback)) return;
                    } catch (SocketTimeoutException ignored) {
                        // Wake periodically so close and expiry are observed promptly.
                    } catch (EOFException | AEADBadTagException ignored) {
                        // Ignore unrelated or tampered LAN traffic and keep this one-time session alive.
                    } catch (GeneralSecurityException | RuntimeException ignored) {
                        // A malformed attempt must not make the valid QR unusable.
                    } catch (IOException ignored) {
                        if (closed || serverSocket.isClosed()) return;
                        // A client can disconnect mid-handshake; the valid QR should remain usable.
                    }
                }
                if (!closed) callback.onFailure("二维码已过期，请生成新的二维码后重试");
            } catch (SocketException error) {
                if (!closed) callback.onFailure("局域网接收已中断，请重新生成二维码");
            } finally {
                close();
            }
        }

        private boolean handleConnection(Socket socket, ReceiverCallback callback)
                throws IOException, GeneralSecurityException {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                sendAck(output, ACK_INVALID_SESSION);
                return false;
            }
            byte[] incomingSession = readBytes(input, 8, 8);
            if (!MessageDigest.isEqual(pairingInfo.sessionId, incomingSession)) {
                sendAck(output, ACK_INVALID_SESSION);
                return false;
            }
            byte[] nonce = readBytes(input, 12, 12);
            byte[] encrypted = readBytes(input, 17, MAX_ENCRYPTED_BYTES);
            byte[] payload = decrypt(encrypted, pairingInfo.secret, pairingInfo.sessionId, nonce);
            if (payload.length == 0 || payload.length > TransferData.MAX_PAYLOAD_BYTES) {
                sendAck(output, ACK_REJECTED);
                return false;
            }
            boolean accepted;
            try {
                accepted = callback.onPayload(payload);
            } catch (RuntimeException ignored) {
                accepted = false;
            }
            try {
                sendAck(output, accepted ? ACK_OK : ACK_REJECTED);
            } catch (IOException error) {
                if (!accepted) throw error;
                // Data was already validated. A lost acknowledgement must not discard it.
            }
            return accepted;
        }

        @Override
        public void close() {
            closed = true;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private NearbyTransferProtocol() {
    }

    static ReceiverSession createReceiver() throws IOException {
        String address = findLanAddress();
        if (address == null) {
            throw new IOException("请先让两台手机连接同一 Wi-Fi，或连接其中一台手机开启的热点");
        }
        return createReceiver(address, DEFAULT_RECEIVE_WINDOW_MS);
    }

    static ReceiverSession createReceiver(String address, long receiveWindowMs) throws IOException {
        if (!isAllowedLanAddress(address)) throw new IOException("局域网地址无效");
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(address, 0));
        byte[] sessionId = randomBytes(8);
        byte[] secret = randomBytes(32);
        PairingInfo pairing = new PairingInfo(address, socket.getLocalPort(), sessionId, secret);
        return new ReceiverSession(socket, pairing, receiveWindowMs);
    }

    static SendResult send(PairingInfo pairing, byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > TransferData.MAX_PAYLOAD_BYTES) {
            return SendResult.failure("迁移数据为空或超过 2 MB");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(pairing.host, pairing.port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            byte[] nonce = randomBytes(12);
            byte[] encrypted = encrypt(payload, pairing.secret, pairing.sessionId, nonce);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            writeBytes(output, pairing.sessionId);
            writeBytes(output, nonce);
            writeBytes(output, encrypted);
            output.flush();

            DataInputStream input = new DataInputStream(socket.getInputStream());
            if (input.readInt() != ACK_MAGIC) {
                return SendResult.failure("新手机返回了无效响应，请重新扫码");
            }
            int status = input.readInt();
            if (status == ACK_OK) return SendResult.success();
            if (status == ACK_INVALID_SESSION) {
                return SendResult.failure("二维码已失效，请在新手机上重新生成");
            }
            return SendResult.failure("新手机未接受这份数据，请重新扫码后再试");
        } catch (SocketTimeoutException error) {
            return SendResult.failure("连接超时，请确认两台手机连接同一 Wi-Fi 或热点");
        } catch (GeneralSecurityException error) {
            return SendResult.failure("无法加密迁移数据，请重新生成二维码");
        } catch (IOException error) {
            return SendResult.failure("无法连接新手机，请保持二维码页面打开并检查局域网连接");
        }
    }

    static byte[] encrypt(byte[] payload, byte[] secret, byte[] sessionId, byte[] nonce)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD_PREFIX);
        cipher.updateAAD(sessionId);
        return cipher.doFinal(payload);
    }

    static byte[] decrypt(byte[] payload, byte[] secret, byte[] sessionId, byte[] nonce)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD_PREFIX);
        cipher.updateAAD(sessionId);
        return cipher.doFinal(payload);
    }

    private static void sendAck(DataOutputStream output, int status) throws IOException {
        output.writeInt(ACK_MAGIC);
        output.writeInt(status);
        output.flush();
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input, int min, int max) throws IOException {
        int length = input.readInt();
        if (length < min || length > max) throw new IOException("Invalid transfer field length");
        byte[] value = new byte[length];
        input.readFully(value);
        return value;
    }

    private static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return value;
    }

    private static String encodeBase64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decodeBase64(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) return result;
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) continue;
            result.put(part.substring(0, separator), part.substring(separator + 1));
        }
        return result;
    }

    private static String required(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("二维码缺少连接信息");
        return value;
    }

    private static String findLanAddress() throws SocketException {
        List<AddressCandidate> candidates = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return null;
        for (NetworkInterface network : Collections.list(interfaces)) {
            if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
            String name = network.getName() == null
                    ? ""
                    : network.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("rmnet") || name.startsWith("pdp") || name.startsWith("ccmni")) {
                continue;
            }
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
                if (!(address instanceof Inet4Address) || !isAllowedLanAddress(address.getHostAddress())) {
                    continue;
                }
                int score = 10;
                if (name.startsWith("wlan") || name.startsWith("wifi")) score += 100;
                else if (name.startsWith("ap") || name.startsWith("swlan")) score += 90;
                else if (name.startsWith("eth")) score += 80;
                if (address.isSiteLocalAddress()) score += 20;
                candidates.add(new AddressCandidate(address.getHostAddress(), score));
            }
        }
        return candidates.stream()
                .max(Comparator.comparingInt(candidate -> candidate.score))
                .map(candidate -> candidate.address)
                .orElse(null);
    }

    private static boolean isAllowedLanAddress(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = raw.split("\\.");
        int[] value = new int[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                value[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException error) {
                return false;
            }
            if (value[i] < 0 || value[i] > 255) return false;
        }
        if (value[0] == 127) return true; // Loopback is only used by local protocol tests.
        if (value[0] == 10 || (value[0] == 192 && value[1] == 168)) return true;
        if (value[0] == 172 && value[1] >= 16 && value[1] <= 31) return true;
        if (value[0] == 169 && value[1] == 254) return true;
        return value[0] == 100 && value[1] >= 64 && value[1] <= 127;
    }

    private static final class AddressCandidate {
        final String address;
        final int score;

        AddressCandidate(String address, int score) {
            this.address = address;
            this.score = score;
        }
    }
}
