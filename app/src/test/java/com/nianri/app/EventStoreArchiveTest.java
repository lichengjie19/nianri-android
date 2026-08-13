package com.nianri.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class EventStoreArchiveTest {
    @Test
    public void deletedDateMovesToRecoverableArchive() {
        DateEvent event = event(42L, "家人生日");
        List<DateEvent> active = new ArrayList<>();
        List<DateEvent> deleted = new ArrayList<>();
        active.add(event);

        assertTrue(EventStore.moveToDeleted(active, deleted, 42L, 1234L));
        assertTrue(active.isEmpty());
        assertEquals(1, deleted.size());
        assertEquals(1234L, deleted.get(0).deletedAt);
    }

    @Test
    public void archivedDateCanBeRestoredWithOriginalData() {
        DateEvent event = event(9L, "结婚纪念日");
        event.deletedAt = 5678L;
        List<DateEvent> active = new ArrayList<>();
        List<DateEvent> deleted = new ArrayList<>();
        deleted.add(event);

        assertTrue(EventStore.restoreFromDeleted(active, deleted, 9L));
        assertTrue(deleted.isEmpty());
        assertEquals(1, active.size());
        assertEquals("结婚纪念日", active.get(0).title);
        assertEquals(0L, active.get(0).deletedAt);
    }

    @Test
    public void permanentDeleteOnlyAffectsRequestedArchivedDate() {
        List<DateEvent> deleted = new ArrayList<>();
        deleted.add(event(1L, "A"));
        deleted.add(event(2L, "B"));

        assertTrue(EventStore.deletePermanentlyFrom(deleted, 1L));
        assertFalse(EventStore.deletePermanentlyFrom(deleted, 3L));
        assertEquals(1, deleted.size());
        assertEquals(2L, deleted.get(0).id);
    }

    @Test
    public void multipleEndedDatesMoveToDeletedWithTimestamp() {
        List<DateEvent> active = new ArrayList<>();
        List<DateEvent> deleted = new ArrayList<>();
        active.add(event(1L, "A"));
        active.add(event(2L, "B"));
        active.add(event(3L, "C"));
        List<Long> ids = new ArrayList<>();
        ids.add(1L);
        ids.add(3L);

        assertEquals(2, EventStore.moveAllToDeleted(active, deleted, ids, 9000L));
        assertEquals(1, active.size());
        assertEquals(2, deleted.size());
        assertEquals(9000L, deleted.get(0).deletedAt);
        assertEquals(9000L, deleted.get(1).deletedAt);
    }

    @Test
    public void clearDeletedRemovesEveryArchivedDate() {
        List<DateEvent> deleted = new ArrayList<>();
        deleted.add(event(1L, "A"));
        deleted.add(event(2L, "B"));

        assertEquals(2, EventStore.clearDeletedFrom(deleted));
        assertTrue(deleted.isEmpty());
        assertEquals(0, EventStore.clearDeletedFrom(deleted));
    }

    private static DateEvent event(long id, String title) {
        DateEvent event = new DateEvent();
        event.id = id;
        event.title = title;
        return event;
    }
}
