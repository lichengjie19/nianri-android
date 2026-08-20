package com.nianri.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class NearbyTransferProtocolTest {
    @Test
    public void pairingQrRoundTripsWithoutLosingSecret() {
        byte[] session = bytes(8, 3);
        byte[] secret = bytes(32, 9);
        NearbyTransferProtocol.PairingInfo original =
                new NearbyTransferProtocol.PairingInfo("192.168.8.21", 42888, session, secret);

        NearbyTransferProtocol.PairingInfo decoded =
                NearbyTransferProtocol.PairingInfo.parse(original.encode());

        assertEquals("192.168.8.21", decoded.host);
        assertEquals(42888, decoded.port);
        assertArrayEquals(session, decoded.sessionId);
        assertArrayEquals(secret, decoded.secret);
        assertEquals(original.safetyCode(), decoded.safetyCode());
    }

    @Test
    public void publicInternetAddressIsRejected() {
        NearbyTransferProtocol.PairingInfo value = new NearbyTransferProtocol.PairingInfo(
                "8.8.8.8",
                40000,
                bytes(8, 1),
                bytes(32, 2)
        );
        boolean rejected = false;
        try {
            NearbyTransferProtocol.PairingInfo.parse(value.encode());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    @Test
    public void authenticatedEncryptionRejectsTampering() throws Exception {
        byte[] payload = "重要日期".getBytes(StandardCharsets.UTF_8);
        byte[] secret = bytes(32, 11);
        byte[] session = bytes(8, 17);
        byte[] nonce = bytes(12, 23);
        byte[] encrypted = NearbyTransferProtocol.encrypt(payload, secret, session, nonce);
        encrypted[encrypted.length - 1] ^= 0x01;

        boolean rejected = false;
        try {
            NearbyTransferProtocol.decrypt(encrypted, secret, session, nonce);
        } catch (GeneralSecurityException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    @Test
    public void localSocketTransferDeliversOnlyValidatedPayload() throws Exception {
        NearbyTransferProtocol.ReceiverSession receiver =
                NearbyTransferProtocol.createReceiver("127.0.0.1", 4_000L);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<byte[]> received = new AtomicReference<>();
        receiver.start(new NearbyTransferProtocol.ReceiverCallback() {
            @Override
            public boolean onPayload(byte[] payload) {
                received.set(payload);
                delivered.countDown();
                return true;
            }

            @Override
            public void onFailure(String message) {
            }
        });

        byte[] payload = "local-only".getBytes(StandardCharsets.UTF_8);
        NearbyTransferProtocol.SendResult result = NearbyTransferProtocol.send(
                receiver.pairingInfo(),
                payload
        );

        assertTrue(result.message, result.success);
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertArrayEquals(payload, received.get());
        receiver.close();
    }

    @Test
    public void tamperedPairingDoesNotInvalidateRealOneTimeSession() throws Exception {
        NearbyTransferProtocol.ReceiverSession receiver =
                NearbyTransferProtocol.createReceiver("127.0.0.1", 4_000L);
        CountDownLatch delivered = new CountDownLatch(1);
        receiver.start(new NearbyTransferProtocol.ReceiverCallback() {
            @Override
            public boolean onPayload(byte[] payload) {
                delivered.countDown();
                return true;
            }

            @Override
            public void onFailure(String message) {
            }
        });
        NearbyTransferProtocol.PairingInfo real = receiver.pairingInfo();
        byte[] wrongSecret = real.secret.clone();
        wrongSecret[0] ^= 0x20;
        NearbyTransferProtocol.PairingInfo tampered = new NearbyTransferProtocol.PairingInfo(
                real.host,
                real.port,
                real.sessionId,
                wrongSecret
        );

        NearbyTransferProtocol.SendResult rejected = NearbyTransferProtocol.send(
                tampered,
                "wrong".getBytes(StandardCharsets.UTF_8)
        );
        NearbyTransferProtocol.SendResult accepted = NearbyTransferProtocol.send(
                real,
                "right".getBytes(StandardCharsets.UTF_8)
        );

        assertFalse("tampered pairing must be rejected", rejected.success);
        assertTrue(accepted.message, accepted.success);
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        receiver.close();
    }

    private static byte[] bytes(int length, int seed) {
        byte[] result = new byte[length];
        for (int i = 0; i < result.length; i++) result[i] = (byte) (seed + i * 7);
        return result;
    }
}
