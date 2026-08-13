package com.nianri.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TagStore {
    public static final String TAG_BIRTHDAY = "birthday";
    public static final String TAG_ANNIVERSARY = "anniversary";
    public static final String TAG_OTHER = "other";

    private static final String LOG_TAG = "TagStore";
    private static final String PREFS = "nianri_tags";
    private static final String KEY_TAGS = "tags_json";
    private static final int MAX_TAGS = 20;

    public static final class Tag {
        public final String id;
        public final String name;
        public final String style;

        Tag(String id, String name, String style) {
            this.id = id;
            this.name = name;
            this.style = style;
        }
    }

    private final Context context;
    private final SharedPreferences preferences;

    public TagStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Tag> load() {
        if (!preferences.contains(KEY_TAGS)) {
            List<Tag> defaults = defaultTags();
            save(defaults);
            return defaults;
        }
        List<Tag> result = new ArrayList<>();
        String raw = preferences.getString(KEY_TAGS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String id = object.optString("id", "").trim();
                String name = object.optString("name", "").trim();
                String style = object.optString("style", "custom").trim();
                if (!id.isEmpty() && !name.isEmpty() && findById(result, id) == null) {
                    result.add(new Tag(id, name, style.isEmpty() ? "custom" : style));
                }
            }
        } catch (Exception error) {
            Log.e(LOG_TAG, "Unable to read tags", error);
        }
        if (result.isEmpty()) {
            result = defaultTags();
            save(result);
        }
        return result;
    }

    public synchronized Tag add(String rawName) {
        List<Tag> tags = load();
        String name = validateName(rawName, tags, null);
        if (tags.size() >= MAX_TAGS) {
            throw new IllegalArgumentException("最多可以创建 " + MAX_TAGS + " 个标签");
        }
        String id = String.format(
                Locale.ROOT,
                "custom_%d_%x",
                System.currentTimeMillis(),
                name.hashCode()
        );
        Tag tag = new Tag(id, name, "custom");
        tags.add(tag);
        save(tags);
        return tag;
    }

    public synchronized void rename(String id, String rawName) {
        List<Tag> tags = load();
        Tag existing = findById(tags, id);
        if (existing == null) {
            throw new IllegalArgumentException("这个标签已不存在");
        }
        String name = validateName(rawName, tags, id);
        List<Tag> updated = new ArrayList<>();
        for (Tag tag : tags) {
            updated.add(tag.id.equals(id) ? new Tag(tag.id, name, tag.style) : tag);
        }
        save(updated);
        updateEvents(id, existing.name, id, name);
    }

    public synchronized String delete(String id) {
        List<Tag> tags = load();
        if (tags.size() <= 1) {
            throw new IllegalArgumentException("至少需要保留一个标签");
        }
        Tag deleted = findById(tags, id);
        if (deleted == null) {
            throw new IllegalArgumentException("这个标签已不存在");
        }
        List<Tag> remaining = new ArrayList<>();
        for (Tag tag : tags) {
            if (!tag.id.equals(id)) remaining.add(tag);
        }
        Tag replacement = findById(remaining, TAG_OTHER);
        if (replacement == null) replacement = remaining.get(0);
        save(remaining);
        updateEvents(deleted.id, deleted.name, replacement.id, replacement.name);
        return replacement.name;
    }

    public Tag resolve(DateEvent event) {
        List<Tag> tags = load();
        Tag byId = findById(tags, event.tagId);
        if (byId != null) return byId;
        for (Tag tag : tags) {
            if (tag.name.equals(event.type)) return tag;
        }
        String legacyId = legacyIdForName(event.type);
        Tag legacy = findById(tags, legacyId);
        if (legacy != null) return legacy;
        Tag other = findById(tags, TAG_OTHER);
        return other == null ? tags.get(0) : other;
    }

    public static String legacyIdForName(String name) {
        if (DateEvent.TYPE_BIRTHDAY.equals(name)) return TAG_BIRTHDAY;
        if (DateEvent.TYPE_ANNIVERSARY.equals(name)) return TAG_ANNIVERSARY;
        return TAG_OTHER;
    }

    static Tag preferredForNewEvent(List<Tag> tags, String preferredId) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个日期标签");
        }
        Tag preferred = findById(tags, preferredId);
        return preferred == null ? tags.get(0) : preferred;
    }

    private void updateEvents(
            String oldId,
            String oldName,
            String newId,
            String newName
    ) {
        EventStore eventStore = new EventStore(context);
        updateEventList(eventStore.load(), eventStore, false, oldId, oldName, newId, newName);
        updateEventList(eventStore.loadDeleted(), eventStore, true, oldId, oldName, newId, newName);
    }

    private void updateEventList(
            List<DateEvent> events,
            EventStore eventStore,
            boolean deleted,
            String oldId,
            String oldName,
            String newId,
            String newName
    ) {
        boolean changed = false;
        for (DateEvent event : events) {
            boolean matches = oldId.equals(event.tagId)
                    || (event.tagId.isEmpty() && oldName.equals(event.type));
            if (matches) {
                event.tagId = newId;
                event.type = newName;
                changed = true;
            }
        }
        if (changed) {
            if (deleted) {
                eventStore.saveDeleted(events);
            } else {
                eventStore.save(events);
            }
        }
    }

    private String validateName(String rawName, List<Tag> tags, String ignoredId) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("请输入标签名称");
        if (name.length() > 12) throw new IllegalArgumentException("标签名称最多 12 个字");
        if ("全部".equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("“全部”是固定的汇总入口");
        }
        for (Tag tag : tags) {
            if (!tag.id.equals(ignoredId) && tag.name.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("已经有同名标签");
            }
        }
        return name;
    }

    private synchronized void save(List<Tag> tags) {
        JSONArray array = new JSONArray();
        try {
            for (Tag tag : tags) {
                JSONObject object = new JSONObject();
                object.put("id", tag.id);
                object.put("name", tag.name);
                object.put("style", tag.style);
                array.put(object);
            }
            preferences.edit().putString(KEY_TAGS, array.toString()).apply();
        } catch (Exception error) {
            Log.e(LOG_TAG, "Unable to save tags", error);
        }
    }

    private static Tag findById(List<Tag> tags, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Tag tag : tags) {
            if (id.equals(tag.id)) return tag;
        }
        return null;
    }

    private static List<Tag> defaultTags() {
        List<Tag> result = new ArrayList<>();
        result.add(new Tag(TAG_BIRTHDAY, DateEvent.TYPE_BIRTHDAY, TAG_BIRTHDAY));
        result.add(new Tag(TAG_ANNIVERSARY, DateEvent.TYPE_ANNIVERSARY, TAG_ANNIVERSARY));
        result.add(new Tag(TAG_OTHER, DateEvent.TYPE_OTHER, TAG_OTHER));
        return result;
    }
}
