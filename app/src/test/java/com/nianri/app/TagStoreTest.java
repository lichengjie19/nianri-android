package com.nianri.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class TagStoreTest {
    @Test
    public void legacyDefaultNamesKeepStableIds() {
        assertEquals(TagStore.TAG_BIRTHDAY, TagStore.legacyIdForName("生日"));
        assertEquals(TagStore.TAG_ANNIVERSARY, TagStore.legacyIdForName("纪念日"));
        assertEquals(TagStore.TAG_OTHER, TagStore.legacyIdForName("其它"));
    }

    @Test
    public void olderOtherSpellingMigratesToOtherTag() {
        assertEquals(TagStore.TAG_OTHER, TagStore.legacyIdForName("其他"));
    }

    @Test
    public void allFilterDefaultsToFirstAvailableTag() {
        List<TagStore.Tag> tags = tags();

        assertEquals(
                TagStore.TAG_BIRTHDAY,
                TagStore.preferredForNewEvent(tags, "__all__").id
        );
    }

    @Test
    public void selectedFilterBecomesNewDateTag() {
        List<TagStore.Tag> tags = tags();

        assertEquals(
                TagStore.TAG_ANNIVERSARY,
                TagStore.preferredForNewEvent(tags, TagStore.TAG_ANNIVERSARY).id
        );
    }

    private static List<TagStore.Tag> tags() {
        List<TagStore.Tag> tags = new ArrayList<>();
        tags.add(new TagStore.Tag(TagStore.TAG_BIRTHDAY, "生日", TagStore.TAG_BIRTHDAY));
        tags.add(new TagStore.Tag(TagStore.TAG_ANNIVERSARY, "纪念日", TagStore.TAG_ANNIVERSARY));
        return tags;
    }
}
