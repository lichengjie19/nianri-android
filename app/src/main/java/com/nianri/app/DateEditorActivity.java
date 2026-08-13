package com.nianri.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.util.List;

public final class DateEditorActivity extends Activity implements DateEditorDialog.Listener {
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_DEFAULT_TAG_ID = "default_tag_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        long eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, 0L);
        String defaultTagId = getIntent().getStringExtra(EXTRA_DEFAULT_TAG_ID);
        DateEvent source = eventId == 0L ? null : findEvent(eventId);
        if (eventId != 0L && source == null) {
            Toast.makeText(this, "这个日期已不存在", Toast.LENGTH_SHORT).show();
            finishAfterTransition();
            return;
        }
        new DateEditorDialog(this, source, defaultTagId, this).showAsPage();
    }

    @Override
    public void onSaved(DateEvent event) {
        new EventStore(this).upsert(event);
        ReminderScheduler.scheduleAll(this);
        Toast.makeText(this, "已保存“" + event.title + "”", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleted(DateEvent event) {
        new EventStore(this).delete(event.id);
        ReminderScheduler.scheduleAll(this);
        Toast.makeText(this, "已移入“回收站”", Toast.LENGTH_SHORT).show();
    }

    private DateEvent findEvent(long id) {
        List<DateEvent> events = new EventStore(this).load();
        for (DateEvent event : events) {
            if (event.id == id) {
                return event;
            }
        }
        return null;
    }
}
