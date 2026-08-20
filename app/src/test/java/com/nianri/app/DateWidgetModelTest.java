package com.nianri.app;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public final class DateWidgetModelTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);

    @Test
    public void autoSelectionUsesNearestUpcomingEvent() {
        DateEvent later = solarEvent(2L, "稍后", LocalDate.of(2026, 7, 20));
        DateEvent nearest = solarEvent(1L, "最近", LocalDate.of(2026, 7, 5));

        DateEvent selected = DateWidgetModel.resolveEvent(
                Arrays.asList(later, nearest),
                DateWidgetModel.AUTO_EVENT_ID,
                TODAY
        );

        assertEquals(nearest.id, selected.id);
    }

    @Test
    public void autoSelectionSkipsExpiredOneTimeEvent() {
        DateEvent expired = solarEvent(1L, "已结束", LocalDate.of(2026, 6, 30));
        DateEvent upcoming = solarEvent(2L, "即将到来", LocalDate.of(2026, 7, 2));

        DateEvent selected = DateWidgetModel.resolveEvent(
                Arrays.asList(expired, upcoming),
                DateWidgetModel.AUTO_EVENT_ID,
                TODAY
        );

        assertEquals(upcoming.id, selected.id);
    }

    @Test
    public void missingExplicitSelectionFallsBackToNearestEvent() {
        DateEvent nearest = solarEvent(2L, "即将到来", LocalDate.of(2026, 7, 2));

        DateEvent selected = DateWidgetModel.resolveEvent(
                Collections.singletonList(nearest),
                99L,
                TODAY
        );

        assertEquals(nearest.id, selected.id);
    }

    @Test
    public void explicitSelectionKeepsExpiredEvent() {
        DateEvent expired = solarEvent(7L, "已结束", LocalDate.of(2026, 6, 28));

        DateEvent selected = DateWidgetModel.resolveEvent(
                Collections.singletonList(expired),
                expired.id,
                TODAY
        );
        DateWidgetModel.CardContent content = DateWidgetModel.content(selected, TODAY);

        assertEquals(expired.id, selected.id);
        assertEquals("3", content.number);
        assertEquals("天前", content.unit);
        assertEquals("2026年6月28日", content.date);
        assertFalse(content.detail.contains("单次日期"));
    }

    @Test
    public void todayAndTomorrowUseAppCountdownWording() {
        DateWidgetModel.CardContent today = DateWidgetModel.content(
                solarEvent(1L, "今天", TODAY),
                TODAY
        );
        DateWidgetModel.CardContent tomorrow = DateWidgetModel.content(
                solarEvent(2L, "明天", TODAY.plusDays(1)),
                TODAY
        );

        assertEquals("今天", today.number);
        assertEquals("", today.unit);
        assertEquals("明天", tomorrow.number);
        assertEquals("", tomorrow.unit);
    }

    @Test
    public void emptyAutoSelectionShowsAddState() {
        DateEvent selected = DateWidgetModel.resolveEvent(
                Collections.emptyList(),
                DateWidgetModel.AUTO_EVENT_ID,
                TODAY
        );
        DateWidgetModel.CardContent content = DateWidgetModel.content(selected, TODAY);

        assertNull(selected);
        assertEquals("添加重要日期", content.title);
        assertEquals("＋", content.number);
        assertEquals("创建后会在这里显示倒数详情", content.detail);
    }

    private static DateEvent solarEvent(long id, String title, LocalDate date) {
        DateEvent event = new DateEvent();
        event.id = id;
        event.createdAt = id;
        event.title = title;
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.year = date.getYear();
        event.month = date.getMonthValue();
        event.day = date.getDayOfMonth();
        event.yearly = false;
        return event;
    }
}
