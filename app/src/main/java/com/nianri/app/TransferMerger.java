package com.nianri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TransferMerger {
    static final class Result {
        final List<DateEvent> events;
        final List<DateEvent> deletedEvents;
        final List<TagStore.Tag> tags;
        final int importedEvents;
        final int importedDeletedEvents;
        final int importedTags;
        final int duplicateEvents;
        final boolean replaced;

        Result(
                List<DateEvent> events,
                List<DateEvent> deletedEvents,
                List<TagStore.Tag> tags,
                int importedEvents,
                int importedDeletedEvents,
                int importedTags,
                int duplicateEvents,
                boolean replaced
        ) {
            this.events = events;
            this.deletedEvents = deletedEvents;
            this.tags = tags;
            this.importedEvents = importedEvents;
            this.importedDeletedEvents = importedDeletedEvents;
            this.importedTags = importedTags;
            this.duplicateEvents = duplicateEvents;
            this.replaced = replaced;
        }
    }

    private static final class TagMerge {
        final List<TagStore.Tag> tags;
        final Map<String, String> importedIds;
        final int added;

        TagMerge(List<TagStore.Tag> tags, Map<String, String> importedIds, int added) {
            this.tags = tags;
            this.importedIds = importedIds;
            this.added = added;
        }
    }

    private static final class EventMerge {
        final List<DateEvent> events;
        final int added;
        final int duplicates;
        final long nextId;

        EventMerge(List<DateEvent> events, int added, int duplicates, long nextId) {
            this.events = events;
            this.added = added;
            this.duplicates = duplicates;
            this.nextId = nextId;
        }
    }

    private TransferMerger() {
    }

    static Result merge(
            List<DateEvent> currentEvents,
            List<DateEvent> currentDeleted,
            List<TagStore.Tag> currentTags,
            TransferData incoming
    ) {
        return merge(
                currentEvents,
                currentDeleted,
                currentTags,
                incoming.events,
                incoming.deletedEvents,
                incoming.tags
        );
    }

    static Result merge(
            List<DateEvent> currentEvents,
            List<DateEvent> currentDeleted,
            List<TagStore.Tag> currentTags,
            List<DateEvent> incomingEvents,
            List<DateEvent> incomingDeleted,
            List<TagStore.Tag> incomingTags
    ) {
        TagMerge tagMerge = mergeTags(currentTags, incomingTags);
        Set<Long> usedIds = collectIds(currentEvents, currentDeleted);
        long nextId = nextId(usedIds);

        EventMerge active = mergeEventList(
                currentEvents,
                incomingEvents,
                tagMerge,
                usedIds,
                nextId,
                false
        );
        EventMerge deleted = mergeEventList(
                currentDeleted,
                incomingDeleted,
                tagMerge,
                usedIds,
                active.nextId,
                true
        );
        return new Result(
                active.events,
                deleted.events,
                tagMerge.tags,
                active.added,
                deleted.added,
                tagMerge.added,
                active.duplicates + deleted.duplicates,
                false
        );
    }

    static Result replace(TransferData incoming) {
        List<TagStore.Tag> tags = copyTags(incoming.tags);
        if (tags.isEmpty()) {
            tags.add(new TagStore.Tag(TagStore.TAG_OTHER, DateEvent.TYPE_OTHER, TagStore.TAG_OTHER));
        }
        TagMerge mapping = directTagMapping(tags);
        Set<Long> usedIds = new HashSet<>();
        EventMerge active = mergeEventList(
                Collections.emptyList(),
                incoming.events,
                mapping,
                usedIds,
                Math.max(1L, System.currentTimeMillis()),
                false
        );
        EventMerge deleted = mergeEventList(
                Collections.emptyList(),
                incoming.deletedEvents,
                mapping,
                usedIds,
                active.nextId,
                true
        );
        return new Result(
                active.events,
                deleted.events,
                tags,
                active.added,
                deleted.added,
                tags.size(),
                active.duplicates + deleted.duplicates,
                true
        );
    }

    private static TagMerge mergeTags(
            List<TagStore.Tag> current,
            List<TagStore.Tag> incoming
    ) {
        List<TagStore.Tag> result = copyTags(current);
        if (result.isEmpty()) {
            result.add(new TagStore.Tag(TagStore.TAG_OTHER, DateEvent.TYPE_OTHER, TagStore.TAG_OTHER));
        }
        Map<String, String> mapping = new HashMap<>();
        int added = 0;
        for (TagStore.Tag imported : incoming) {
            TagStore.Tag byName = findByName(result, imported.name);
            if (byName != null) {
                mapping.put(imported.id, byName.id);
                continue;
            }
            TagStore.Tag byId = findById(result, imported.id);
            if (byId == null && result.size() < TagStore.MAX_TAGS) {
                result.add(copyTag(imported));
                mapping.put(imported.id, imported.id);
                added++;
                continue;
            }
            if (result.size() < TagStore.MAX_TAGS) {
                String newId = uniqueImportedTagId(imported, result);
                result.add(new TagStore.Tag(newId, imported.name, imported.style));
                mapping.put(imported.id, newId);
                added++;
                continue;
            }
            mapping.put(imported.id, fallbackTag(result).id);
        }
        return new TagMerge(result, mapping, added);
    }

    private static TagMerge directTagMapping(List<TagStore.Tag> tags) {
        Map<String, String> mapping = new HashMap<>();
        for (TagStore.Tag tag : tags) mapping.put(tag.id, tag.id);
        return new TagMerge(tags, mapping, tags.size());
    }

    private static EventMerge mergeEventList(
            List<DateEvent> current,
            List<DateEvent> incoming,
            TagMerge tagMerge,
            Set<Long> usedIds,
            long firstNextId,
            boolean deleted
    ) {
        List<DateEvent> result = copyEvents(current);
        Set<String> fingerprints = new HashSet<>();
        for (DateEvent event : result) fingerprints.add(fingerprint(event));
        long nextId = firstNextId;
        int added = 0;
        int duplicates = 0;
        for (DateEvent source : incoming) {
            DateEvent event = source.copy();
            remapTag(event, tagMerge);
            event.deletedAt = deleted ? Math.max(1L, event.deletedAt) : 0L;
            String fingerprint = fingerprint(event);
            if (fingerprints.contains(fingerprint)) {
                duplicates++;
                continue;
            }
            if (event.id <= 0L || usedIds.contains(event.id)) {
                while (usedIds.contains(nextId) || nextId <= 0L) nextId++;
                event.id = nextId++;
            }
            usedIds.add(event.id);
            if (event.createdAt <= 0L) event.createdAt = event.id;
            fingerprints.add(fingerprint);
            result.add(event);
            added++;
        }
        return new EventMerge(result, added, duplicates, nextId);
    }

    private static void remapTag(DateEvent event, TagMerge tagMerge) {
        String mappedId = tagMerge.importedIds.get(event.tagId);
        TagStore.Tag tag = mappedId == null ? findById(tagMerge.tags, event.tagId) : findById(tagMerge.tags, mappedId);
        if (tag == null) tag = findByName(tagMerge.tags, event.type);
        if (tag == null) tag = fallbackTag(tagMerge.tags);
        event.tagId = tag.id;
        event.type = tag.name;
    }

    private static String fingerprint(DateEvent event) {
        StringBuilder value = new StringBuilder();
        append(value, event.title == null ? "" : event.title.trim());
        append(value, event.tagId);
        append(value, event.calendarType);
        append(value, event.yearKnown ? "1" : "0");
        append(value, event.yearKnown ? String.valueOf(event.year) : "");
        append(value, String.valueOf(event.month));
        append(value, String.valueOf(event.day));
        append(value, event.leapMonth ? "1" : "0");
        append(value, event.yearly ? "1" : "0");
        append(value, event.reminderEnabled ? "1" : "0");
        append(value, event.followSystemAlert ? "1" : "0");
        append(value, event.alertSound ? "1" : "0");
        append(value, event.alertVibration ? "1" : "0");
        append(value, String.valueOf(event.reminderHour));
        List<Integer> reminderDays = new ArrayList<>(event.reminderDays);
        Collections.sort(reminderDays);
        append(value, reminderDays.toString());
        append(value, event.note);
        append(value, event.externalId);
        return value.toString();
    }

    private static void append(StringBuilder target, String raw) {
        String value = raw == null ? "" : raw;
        target.append(value.length()).append(':').append(value).append('|');
    }

    private static Set<Long> collectIds(List<DateEvent> first, List<DateEvent> second) {
        Set<Long> result = new HashSet<>();
        for (DateEvent event : first) if (event.id > 0L) result.add(event.id);
        for (DateEvent event : second) if (event.id > 0L) result.add(event.id);
        return result;
    }

    private static long nextId(Set<Long> usedIds) {
        long value = Math.max(1L, System.currentTimeMillis());
        for (long id : usedIds) value = Math.max(value, id + 1L);
        return value;
    }

    private static String uniqueImportedTagId(TagStore.Tag tag, List<TagStore.Tag> existing) {
        String base = "imported_" + Integer.toHexString((tag.id + "\n" + tag.name).hashCode());
        String candidate = base;
        int suffix = 2;
        while (findById(existing, candidate) != null) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static TagStore.Tag fallbackTag(List<TagStore.Tag> tags) {
        TagStore.Tag other = findById(tags, TagStore.TAG_OTHER);
        return other == null ? tags.get(0) : other;
    }

    private static TagStore.Tag findById(List<TagStore.Tag> tags, String id) {
        if (id == null) return null;
        for (TagStore.Tag tag : tags) if (id.equals(tag.id)) return tag;
        return null;
    }

    private static TagStore.Tag findByName(List<TagStore.Tag> tags, String name) {
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (TagStore.Tag tag : tags) {
            if (tag.name.trim().toLowerCase(Locale.ROOT).equals(normalized)) return tag;
        }
        return null;
    }

    private static List<DateEvent> copyEvents(List<DateEvent> source) {
        List<DateEvent> result = new ArrayList<>();
        for (DateEvent event : source) result.add(event.copy());
        return result;
    }

    private static List<TagStore.Tag> copyTags(List<TagStore.Tag> source) {
        List<TagStore.Tag> result = new ArrayList<>();
        for (TagStore.Tag tag : source) result.add(copyTag(tag));
        return result;
    }

    private static TagStore.Tag copyTag(TagStore.Tag tag) {
        return new TagStore.Tag(tag.id, tag.name, tag.style);
    }
}
