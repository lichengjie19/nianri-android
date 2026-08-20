package com.nianri.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TransferMergerTest {
    @Test
    public void mergeSkipsEquivalentDateEvenWhenIdsDiffer() {
        DateEvent current = event(1L, "妈妈生日", TagStore.TAG_BIRTHDAY, "生日", 5, 8);
        DateEvent incoming = current.copy();
        incoming.id = 99L;

        TransferMerger.Result result = TransferMerger.merge(
                list(current),
                new ArrayList<>(),
                defaultTags(),
                list(incoming),
                new ArrayList<>(),
                defaultTags()
        );

        assertEquals(1, result.events.size());
        assertEquals(0, result.importedEvents);
        assertEquals(1, result.duplicateEvents);
    }

    @Test
    public void collidingEventIdIsRegeneratedWithoutOverwritingCurrentDate() {
        DateEvent current = event(7L, "妈妈生日", TagStore.TAG_BIRTHDAY, "生日", 5, 8);
        DateEvent incoming = event(7L, "结婚纪念日", TagStore.TAG_ANNIVERSARY, "纪念日", 10, 2);

        TransferMerger.Result result = TransferMerger.merge(
                list(current),
                new ArrayList<>(),
                defaultTags(),
                list(incoming),
                new ArrayList<>(),
                defaultTags()
        );

        assertEquals(2, result.events.size());
        assertEquals(1, result.importedEvents);
        assertEquals(7L, result.events.get(0).id);
        assertNotEquals(7L, result.events.get(1).id);
    }

    @Test
    public void renamedIncomingTagWithSameIdIsSafelySplit() {
        List<TagStore.Tag> incomingTags = new ArrayList<>();
        incomingTags.add(new TagStore.Tag(TagStore.TAG_BIRTHDAY, "家人生日", "birthday"));
        DateEvent incoming = event(20L, "爸爸生日", TagStore.TAG_BIRTHDAY, "家人生日", 7, 1);

        TransferMerger.Result result = TransferMerger.merge(
                new ArrayList<>(),
                new ArrayList<>(),
                defaultTags(),
                list(incoming),
                new ArrayList<>(),
                incomingTags
        );

        assertEquals(4, result.tags.size());
        assertEquals(1, result.importedTags);
        assertEquals("家人生日", result.events.get(0).type);
        assertNotEquals(TagStore.TAG_BIRTHDAY, result.events.get(0).tagId);
    }

    @Test
    public void deletedDatesRemainInRecycleBinDuringMerge() {
        DateEvent deleted = event(30L, "旧事项", TagStore.TAG_OTHER, "其它", 3, 6);
        deleted.deletedAt = 1234L;

        TransferMerger.Result result = TransferMerger.merge(
                new ArrayList<>(),
                new ArrayList<>(),
                defaultTags(),
                new ArrayList<>(),
                list(deleted),
                defaultTags()
        );

        assertTrue(result.events.isEmpty());
        assertEquals(1, result.deletedEvents.size());
        assertEquals(1234L, result.deletedEvents.get(0).deletedAt);
        assertEquals(1, result.importedDeletedEvents);
    }

    private static DateEvent event(
            long id,
            String title,
            String tagId,
            String type,
            int month,
            int day
    ) {
        DateEvent event = new DateEvent();
        event.id = id;
        event.createdAt = id;
        event.title = title;
        event.tagId = tagId;
        event.type = type;
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.yearKnown = true;
        event.year = 2026;
        event.month = month;
        event.day = day;
        return event;
    }

    private static List<TagStore.Tag> defaultTags() {
        return new ArrayList<>(Arrays.asList(
                new TagStore.Tag(TagStore.TAG_BIRTHDAY, "生日", "birthday"),
                new TagStore.Tag(TagStore.TAG_ANNIVERSARY, "纪念日", "anniversary"),
                new TagStore.Tag(TagStore.TAG_OTHER, "其它", "other")
        ));
    }

    private static List<DateEvent> list(DateEvent event) {
        List<DateEvent> result = new ArrayList<>();
        result.add(event);
        return result;
    }
}
