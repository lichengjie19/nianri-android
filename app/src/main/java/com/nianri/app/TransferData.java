package com.nianri.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TransferData {
    static final String FORMAT = "nianri-nearby-transfer";
    static final int SCHEMA_VERSION = 1;
    static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final int MAX_EVENTS_PER_LIST = 5_000;

    final List<DateEvent> events;
    final List<DateEvent> deletedEvents;
    final List<TagStore.Tag> tags;
    final String sourceVersion;
    final String sourceDevice;
    final long exportedAt;

    private TransferData(
            List<DateEvent> events,
            List<DateEvent> deletedEvents,
            List<TagStore.Tag> tags,
            String sourceVersion,
            String sourceDevice,
            long exportedAt
    ) {
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.tags = tags;
        this.sourceVersion = sourceVersion;
        this.sourceDevice = sourceDevice;
        this.exportedAt = exportedAt;
    }

    static TransferData capture(Context context, boolean includeDeleted) {
        EventStore eventStore = new EventStore(context);
        TagStore tagStore = new TagStore(context);
        return new TransferData(
                copyEvents(eventStore.load()),
                includeDeleted ? copyEvents(eventStore.loadDeleted()) : new ArrayList<>(),
                copyTags(tagStore.load()),
                versionName(context),
                deviceName(),
                System.currentTimeMillis()
        );
    }

    byte[] encode() {
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("sourceVersion", sourceVersion);
            root.put("sourceDevice", sourceDevice);
            root.put("exportedAt", exportedAt);

            JSONArray active = new JSONArray();
            for (DateEvent event : events) active.put(event.toJson());
            root.put("events", active);

            JSONArray deleted = new JSONArray();
            for (DateEvent event : deletedEvents) deleted.put(event.toJson());
            root.put("deletedEvents", deleted);

            JSONArray tagArray = new JSONArray();
            for (TagStore.Tag tag : tags) {
                JSONObject object = new JSONObject();
                object.put("id", tag.id);
                object.put("name", tag.name);
                object.put("style", tag.style);
                tagArray.put(object);
            }
            root.put("tags", tagArray);

            byte[] value = root.toString().getBytes(StandardCharsets.UTF_8);
            if (value.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("迁移数据超过 2 MB，暂时无法直接传输");
            }
            return value;
        } catch (JSONException error) {
            throw new IllegalStateException("无法整理迁移数据", error);
        }
    }

    static TransferData decode(byte[] value) {
        if (value == null || value.length == 0 || value.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("迁移数据为空或过大");
        }
        try {
            JSONObject root = new JSONObject(new String(value, StandardCharsets.UTF_8));
            if (!FORMAT.equals(root.optString("format"))) {
                throw new IllegalArgumentException("这不是念日换机数据");
            }
            int schema = root.optInt("schemaVersion", 0);
            if (schema < 1) {
                throw new IllegalArgumentException("迁移数据格式无效");
            }
            if (schema > SCHEMA_VERSION) {
                throw new IllegalArgumentException("旧手机的念日版本更新，请先更新新手机再迁移");
            }

            List<DateEvent> active = readEvents(root.optJSONArray("events"), false);
            List<DateEvent> deleted = readEvents(root.optJSONArray("deletedEvents"), true);
            List<TagStore.Tag> tags = readTags(root.optJSONArray("tags"));
            if (tags.isEmpty()) tags = defaultTags();
            return new TransferData(
                    active,
                    deleted,
                    tags,
                    cleanText(root.optString("sourceVersion", "未知版本"), 40, "未知版本"),
                    cleanText(root.optString("sourceDevice", "旧手机"), 80, "旧手机"),
                    root.optLong("exportedAt", System.currentTimeMillis())
            );
        } catch (JSONException error) {
            throw new IllegalArgumentException("迁移数据已损坏", error);
        }
    }

    int totalEventCount() {
        return events.size() + deletedEvents.size();
    }

    private static List<DateEvent> readEvents(JSONArray array, boolean deleted) {
        List<DateEvent> result = new ArrayList<>();
        if (array == null) return result;
        if (array.length() > MAX_EVENTS_PER_LIST) {
            throw new IllegalArgumentException("迁移日期数量超过上限");
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) throw new IllegalArgumentException("第 " + (i + 1) + " 条日期格式无效");
            DateEvent event = DateEvent.fromJson(object);
            validateEvent(event, i + 1);
            event.deletedAt = deleted
                    ? Math.max(1L, event.deletedAt)
                    : 0L;
            result.add(event);
        }
        return result;
    }

    private static void validateEvent(DateEvent event, int position) {
        event.title = cleanText(event.title, 200, "");
        if (event.title.isEmpty()) {
            throw new IllegalArgumentException("第 " + position + " 条日期没有标题");
        }
        event.type = cleanText(event.type, 40, DateEvent.TYPE_OTHER);
        event.tagId = cleanText(event.tagId, 100, TagStore.legacyIdForName(event.type));
        event.note = cleanText(event.note, 20_000, "");
        event.externalId = cleanText(event.externalId, 500, "");
        if (!DateEvent.CALENDAR_SOLAR.equals(event.calendarType)
                && !DateEvent.CALENDAR_LUNAR.equals(event.calendarType)) {
            throw new IllegalArgumentException("第 " + position + " 条日期的历法无效");
        }
        if (event.month < 1 || event.month > 12 || event.day < 1 || event.day > 31) {
            throw new IllegalArgumentException("第 " + position + " 条日期的月日无效");
        }
        if (event.yearKnown && (event.year < 1900 || event.year > 2100)) {
            throw new IllegalArgumentException("第 " + position + " 条日期超出 1900—2100 年范围");
        }
        if (event.reminderDays.size() > 64) {
            throw new IllegalArgumentException("第 " + position + " 条日期的提醒设置过多");
        }
        for (int day : event.reminderDays) {
            if (day < 0 || day > 365) {
                throw new IllegalArgumentException("第 " + position + " 条日期的提醒设置无效");
            }
        }
        if (event.createdAt <= 0L) event.createdAt = event.id;
    }

    private static List<TagStore.Tag> readTags(JSONArray array) {
        List<TagStore.Tag> result = new ArrayList<>();
        if (array == null) return result;
        if (array.length() > TagStore.MAX_TAGS) {
            throw new IllegalArgumentException("迁移标签数量超过上限");
        }
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) throw new IllegalArgumentException("迁移标签格式无效");
            String id = cleanText(object.optString("id"), 100, "");
            String name = cleanText(object.optString("name"), 12, "");
            String style = cleanText(object.optString("style"), 40, "custom");
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (id.isEmpty() || name.isEmpty() || ids.contains(id) || names.contains(normalizedName)) {
                throw new IllegalArgumentException("迁移标签包含空值或重复项");
            }
            ids.add(id);
            names.add(normalizedName);
            result.add(new TagStore.Tag(id, name, style));
        }
        return result;
    }

    private static String cleanText(String value, int maxLength, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return fallback;
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("迁移数据包含过长文本");
        }
        return text;
    }

    private static List<DateEvent> copyEvents(List<DateEvent> source) {
        List<DateEvent> result = new ArrayList<>();
        for (DateEvent event : source) result.add(event.copy());
        return result;
    }

    private static List<TagStore.Tag> copyTags(List<TagStore.Tag> source) {
        List<TagStore.Tag> result = new ArrayList<>();
        for (TagStore.Tag tag : source) {
            result.add(new TagStore.Tag(tag.id, tag.name, tag.style));
        }
        return result;
    }

    private static List<TagStore.Tag> defaultTags() {
        List<TagStore.Tag> result = new ArrayList<>();
        result.add(new TagStore.Tag(TagStore.TAG_BIRTHDAY, DateEvent.TYPE_BIRTHDAY, TagStore.TAG_BIRTHDAY));
        result.add(new TagStore.Tag(TagStore.TAG_ANNIVERSARY, DateEvent.TYPE_ANNIVERSARY, TagStore.TAG_ANNIVERSARY));
        result.add(new TagStore.Tag(TagStore.TAG_OTHER, DateEvent.TYPE_OTHER, TagStore.TAG_OTHER));
        return result;
    }

    private static String versionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "未知版本" : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "未知版本";
        }
    }

    private static String deviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (manufacturer.isEmpty()) return model.isEmpty() ? "旧手机" : model;
        if (model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT))) {
            return model;
        }
        return (manufacturer + " " + model).trim();
    }
}
