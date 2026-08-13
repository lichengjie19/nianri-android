package com.nianri.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class EventStore {
    private static final String TAG = "EventStore";
    private static final String PREFS = "nianri_events";
    private static final String KEY_EVENTS = "events_json";
    private static final String KEY_DELETED_EVENTS = "deleted_events_json";
    private static final String KEY_SEEDED = "demo_seeded";

    private final SharedPreferences preferences;

    public EventStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<DateEvent> load() {
        return read(KEY_EVENTS);
    }

    public synchronized List<DateEvent> loadDeleted() {
        return read(KEY_DELETED_EVENTS);
    }

    private List<DateEvent> read(String key) {
        String raw = preferences.getString(key, "[]");
        List<DateEvent> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    result.add(DateEvent.fromJson(object));
                }
            }
        } catch (Exception error) {
            Log.e(TAG, "Unable to read saved dates", error);
        }
        return result;
    }

    public synchronized void save(List<DateEvent> events) {
        write(KEY_EVENTS, events);
    }

    public synchronized void saveDeleted(List<DateEvent> events) {
        write(KEY_DELETED_EVENTS, events);
    }

    private void write(String key, List<DateEvent> events) {
        JSONArray array = new JSONArray();
        try {
            for (DateEvent event : events) {
                array.put(event.toJson());
            }
            preferences.edit().putString(key, array.toString()).apply();
        } catch (Exception error) {
            Log.e(TAG, "Unable to save dates", error);
        }
    }

    public synchronized void upsert(DateEvent event) {
        List<DateEvent> events = load();
        upsertInto(events, event);
        save(events);
    }

    public synchronized void delete(long id) {
        List<DateEvent> events = load();
        List<DateEvent> deleted = loadDeleted();
        if (!moveToDeleted(events, deleted, id, System.currentTimeMillis())) return;
        save(events);
        saveDeleted(deleted);
    }

    public synchronized boolean restore(long id) {
        List<DateEvent> events = load();
        List<DateEvent> deleted = loadDeleted();
        if (!restoreFromDeleted(events, deleted, id)) return false;
        save(events);
        saveDeleted(deleted);
        return true;
    }

    public synchronized boolean deletePermanently(long id) {
        List<DateEvent> deleted = loadDeleted();
        boolean removed = deletePermanentlyFrom(deleted, id);
        if (removed) saveDeleted(deleted);
        return removed;
    }

    public synchronized int moveAllToDeleted(List<Long> ids) {
        List<DateEvent> events = load();
        List<DateEvent> deleted = loadDeleted();
        int moved = moveAllToDeleted(
                events,
                deleted,
                ids,
                System.currentTimeMillis()
        );
        if (moved > 0) {
            save(events);
            saveDeleted(deleted);
        }
        return moved;
    }

    public synchronized int clearDeleted() {
        List<DateEvent> deleted = loadDeleted();
        int removed = clearDeletedFrom(deleted);
        if (removed > 0) saveDeleted(deleted);
        return removed;
    }

    static boolean moveToDeleted(
            List<DateEvent> events,
            List<DateEvent> deleted,
            long id,
            long deletedAt
    ) {
        DateEvent removed = find(events, id);
        if (removed == null) return false;
        events.remove(removed);
        deleted.removeIf(event -> event.id == id);
        removed.deletedAt = deletedAt;
        deleted.add(removed);
        return true;
    }

    static boolean restoreFromDeleted(
            List<DateEvent> events,
            List<DateEvent> deleted,
            long id
    ) {
        DateEvent restored = find(deleted, id);
        if (restored == null) return false;
        deleted.remove(restored);
        restored.deletedAt = 0L;
        upsertInto(events, restored);
        return true;
    }

    static boolean deletePermanentlyFrom(List<DateEvent> deleted, long id) {
        return deleted.removeIf(event -> event.id == id);
    }

    static int moveAllToDeleted(
            List<DateEvent> events,
            List<DateEvent> deleted,
            List<Long> ids,
            long deletedAt
    ) {
        int moved = 0;
        for (long id : ids) {
            if (moveToDeleted(events, deleted, id, deletedAt)) moved++;
        }
        return moved;
    }

    static int clearDeletedFrom(List<DateEvent> deleted) {
        int removed = deleted.size();
        deleted.clear();
        return removed;
    }

    private static void upsertInto(List<DateEvent> events, DateEvent value) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).id == value.id) {
                events.set(i, value);
                return;
            }
        }
        events.add(value);
    }

    private static DateEvent find(List<DateEvent> events, long id) {
        for (DateEvent event : events) {
            if (event.id == id) return event;
        }
        return null;
    }

    public void seedDemoDatesIfNeeded() {
        if (preferences.getBoolean(KEY_SEEDED, false)) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<DateEvent> demo = new ArrayList<>();
        long baseId = System.currentTimeMillis();

        LocalDate birthdayDate = today.plusDays(12);
        Lunar birthdayLunar = Solar.fromYmd(
                birthdayDate.getYear(), birthdayDate.getMonthValue(), birthdayDate.getDayOfMonth()
        ).getLunar();
        DateEvent birthday = makeBase(baseId, "妈妈生日", DateEvent.TYPE_BIRTHDAY);
        birthday.calendarType = DateEvent.CALENDAR_LUNAR;
        birthday.yearKnown = true;
        birthday.year = birthdayLunar.getYear();
        birthday.month = Math.abs(birthdayLunar.getMonth());
        birthday.day = birthdayLunar.getDay();
        birthday.leapMonth = birthdayLunar.getMonth() < 0;
        birthday.yearly = true;
        birthday.reminderDays.clear();
        birthday.reminderDays.add(0);
        birthday.reminderDays.add(1);
        birthday.reminderDays.add(7);
        demo.add(birthday);

        LocalDate anniversaryDate = today.plusDays(47);
        DateEvent anniversary = makeBase(baseId + 1, "结婚纪念日", DateEvent.TYPE_ANNIVERSARY);
        setSolarDate(anniversary, anniversaryDate);
        anniversary.yearly = true;
        demo.add(anniversary);

        LocalDate checkupDate = today.plusDays(95);
        DateEvent checkup = makeBase(baseId + 2, "年度体检", DateEvent.TYPE_OTHER);
        setSolarDate(checkup, checkupDate);
        checkup.yearly = false;
        checkup.reminderDays.add(1);
        demo.add(checkup);

        LocalDate expiredDate = today.minusDays(2);
        DateEvent expired = makeBase(baseId + 3, "牙科复查", DateEvent.TYPE_OTHER);
        setSolarDate(expired, expiredDate);
        expired.yearly = false;
        demo.add(expired);

        save(demo);
        preferences.edit().putBoolean(KEY_SEEDED, true).apply();
    }

    private static DateEvent makeBase(long id, String title, String type) {
        DateEvent event = new DateEvent();
        event.id = id;
        event.createdAt = id;
        event.title = title;
        event.type = type;
        event.tagId = TagStore.legacyIdForName(type);
        return event;
    }

    private static void setSolarDate(DateEvent event, LocalDate date) {
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.yearKnown = true;
        event.year = date.getYear();
        event.month = date.getMonthValue();
        event.day = date.getDayOfMonth();
    }
}
