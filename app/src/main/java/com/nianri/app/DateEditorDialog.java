package com.nianri.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class DateEditorDialog {
    public interface Listener {
        void onSaved(DateEvent event);

        void onDeleted(DateEvent event);
    }

    private static final String[] LUNAR_MONTHS = {
            "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二"
    };
    private static final String[] LUNAR_DAYS = {
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    private final Activity activity;
    private final Listener listener;
    private final DateEvent draft;
    private final boolean editing;
    private final List<TagStore.Tag> tags;

    private EditText titleInput;
    private TextView[] typeChips;
    private int selectedTagIndex;
    private RadioGroup calendarGroup;
    private RadioButton solarRadio;
    private RadioButton lunarRadio;
    private Switch yearKnownSwitch;
    private TextView dateButton;
    private TextView dateHint;
    private Switch yearlySwitch;
    private Switch reminderEnabledSwitch;
    private Switch followSystemAlertSwitch;
    private LinearLayout customAlertOptions;
    private Switch eventSoundSwitch;
    private Switch eventVibrationSwitch;
    private Switch leapCheck;
    private LinearLayout reminderRow;
    private CheckBox reminderToday;
    private CheckBox reminderOne;
    private CheckBox reminderSeven;
    private TextView reminderTimeButton;
    private TextView reminderOffHint;
    private EditText noteInput;

    public DateEditorDialog(
            Activity activity,
            DateEvent source,
            String defaultTagId,
            Listener listener
    ) {
        this.activity = activity;
        this.listener = listener;
        editing = source != null;
        draft = source == null ? createDefault() : source.copy();
        TagStore tagStore = new TagStore(activity);
        tags = tagStore.load();
        if (!editing) {
            TagStore.Tag initialTag = TagStore.preferredForNewEvent(tags, defaultTagId);
            draft.tagId = initialTag.id;
            draft.type = initialTag.name;
        }
        TagStore.Tag resolvedTag = tagStore.resolve(draft);
        draft.tagId = resolvedTag.id;
        draft.type = resolvedTag.name;
        selectedTagIndex = tagIndex(draft.tagId);
    }

    public void showAsPage() {
        showEditor();
    }

    private void showEditor() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout content = Ui.vertical(activity);
        int side = Ui.dp(activity, 20);
        content.setPadding(
                side,
                Ui.dp(activity, 18),
                side,
                Ui.dp(activity, 30)
        );
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout pageHeader = Ui.horizontal(activity);
        ImageButton back = new ImageButton(activity);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setBackground(Ui.ripple(activity, Color.rgb(236, 234, 228), 999));
        back.setPadding(
                Ui.dp(activity, 10),
                Ui.dp(activity, 10),
                Ui.dp(activity, 10),
                Ui.dp(activity, 10)
        );
        back.setContentDescription("返回首页");
        back.setOnClickListener(view -> activity.finishAfterTransition());
        pageHeader.addView(back, Ui.linearParams(Ui.dp(activity, 44), Ui.dp(activity, 44)));

        LinearLayout heading = Ui.vertical(activity);
        TextView pageTitle = Ui.text(activity, editing ? "编辑日期" : "添加日期", 23, Ui.TEXT, true);
        TextView pageSubtitle = Ui.text(
                activity,
                editing ? "更新日期与提醒设置" : "记下值得被提醒的一天",
                12,
                Ui.MUTED,
                false
        );
        heading.addView(pageTitle, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams pageSubtitleParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pageSubtitleParams.topMargin = Ui.dp(activity, 5);
        heading.addView(pageSubtitle, pageSubtitleParams);
        LinearLayout.LayoutParams headingParams = Ui.weightedParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        headingParams.leftMargin = Ui.dp(activity, 13);
        pageHeader.addView(heading, headingParams);
        content.addView(pageHeader, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        titleInput = new EditText(activity);
        titleInput.setSingleLine(true);
        titleInput.setText(draft.title);
        titleInput.setHint("例如：爸爸生日");
        titleInput.setTextColor(Ui.TEXT);
        titleInput.setHintTextColor(Ui.MUTED);
        titleInput.setTextSize(15);
        titleInput.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);
        titleInput.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 15, Ui.BORDER, 1));
        addField(content, "名称", titleInput, Ui.dp(activity, 52));

        addField(content, "类型", createTypeSelector(), Ui.dp(activity, 42));

        calendarGroup = new RadioGroup(activity);
        calendarGroup.setOrientation(LinearLayout.HORIZONTAL);
        calendarGroup.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4), Ui.dp(activity, 8), Ui.dp(activity, 4));
        calendarGroup.setBackground(Ui.rounded(activity, Color.rgb(236, 234, 228), 15));
        solarRadio = radio("公历", DateEvent.CALENDAR_SOLAR.equals(draft.calendarType));
        lunarRadio = radio("农历", DateEvent.CALENDAR_LUNAR.equals(draft.calendarType));
        calendarGroup.addView(solarRadio, Ui.weightedParams(Ui.dp(activity, 44), 1));
        calendarGroup.addView(lunarRadio, Ui.weightedParams(Ui.dp(activity, 44), 1));
        calendarGroup.check(DateEvent.CALENDAR_LUNAR.equals(draft.calendarType)
                ? lunarRadio.getId()
                : solarRadio.getId());
        addField(content, "日期历法", calendarGroup, Ui.dp(activity, 52));

        yearKnownSwitch = new Switch(activity);
        yearKnownSwitch.setText("记录年份（可选）");
        yearKnownSwitch.setTextColor(Ui.TEXT);
        yearKnownSwitch.setTextSize(14);
        yearKnownSwitch.setChecked(draft.yearKnown);
        yearKnownSwitch.setPadding(Ui.dp(activity, 2), Ui.dp(activity, 8), Ui.dp(activity, 2), Ui.dp(activity, 4));
        LinearLayout.LayoutParams yearKnownParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        yearKnownParams.setMargins(0, Ui.dp(activity, 10), 0, 0);
        content.addView(yearKnownSwitch, yearKnownParams);

        dateButton = Ui.button(activity, "", Ui.TEXT, Ui.SURFACE, 15);
        dateButton.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        addField(content, "日期（月、日必填）", dateButton, Ui.dp(activity, 54));
        dateHint = Ui.text(activity, "", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams hintParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintParams.setMargins(Ui.dp(activity, 4), Ui.dp(activity, 6), Ui.dp(activity, 4), 0);
        content.addView(dateHint, hintParams);

        leapCheck = new Switch(activity);
        leapCheck.setText("这是闰月日期");
        leapCheck.setTextColor(Ui.TEXT);
        leapCheck.setTextSize(14);
        leapCheck.setChecked(draft.leapMonth);
        LinearLayout.LayoutParams leapParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        leapParams.setMargins(0, Ui.dp(activity, 8), 0, 0);
        content.addView(leapCheck, leapParams);

        yearlySwitch = new Switch(activity);
        yearlySwitch.setText("每年自动重复");
        yearlySwitch.setTextColor(Ui.TEXT);
        yearlySwitch.setTextSize(14);
        yearlySwitch.setChecked(draft.yearly);
        yearlySwitch.setPadding(Ui.dp(activity, 2), Ui.dp(activity, 7), Ui.dp(activity, 2), Ui.dp(activity, 7));
        LinearLayout.LayoutParams switchParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        switchParams.setMargins(0, Ui.dp(activity, 12), 0, 0);
        content.addView(yearlySwitch, switchParams);

        reminderEnabledSwitch = new Switch(activity);
        reminderEnabledSwitch.setText("开启提醒");
        reminderEnabledSwitch.setTextColor(Ui.TEXT);
        reminderEnabledSwitch.setTextSize(14);
        reminderEnabledSwitch.setChecked(draft.reminderEnabled);
        reminderEnabledSwitch.setPadding(
                Ui.dp(activity, 2),
                Ui.dp(activity, 7),
                Ui.dp(activity, 2),
                Ui.dp(activity, 7)
        );
        LinearLayout.LayoutParams reminderEnabledParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        reminderEnabledParams.setMargins(0, Ui.dp(activity, 4), 0, 0);
        content.addView(reminderEnabledSwitch, reminderEnabledParams);

        followSystemAlertSwitch = new Switch(activity);
        followSystemAlertSwitch.setText("跟随系统声音与振动");
        followSystemAlertSwitch.setTextColor(Ui.TEXT);
        followSystemAlertSwitch.setTextSize(14);
        followSystemAlertSwitch.setChecked(draft.followSystemAlert);
        followSystemAlertSwitch.setPadding(
                Ui.dp(activity, 2),
                Ui.dp(activity, 7),
                Ui.dp(activity, 2),
                Ui.dp(activity, 7)
        );
        content.addView(followSystemAlertSwitch, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        customAlertOptions = Ui.horizontal(activity);
        customAlertOptions.setPadding(
                Ui.dp(activity, 10),
                Ui.dp(activity, 3),
                Ui.dp(activity, 10),
                Ui.dp(activity, 3)
        );
        customAlertOptions.setBackground(Ui.roundedStroke(
                activity,
                Ui.SURFACE,
                14,
                Ui.BORDER,
                1
        ));
        eventSoundSwitch = new Switch(activity);
        eventSoundSwitch.setText("声音");
        eventSoundSwitch.setTextColor(Ui.TEXT);
        eventSoundSwitch.setTextSize(13);
        eventSoundSwitch.setChecked(draft.alertSound);
        customAlertOptions.addView(eventSoundSwitch, Ui.weightedParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        eventVibrationSwitch = new Switch(activity);
        eventVibrationSwitch.setText("振动");
        eventVibrationSwitch.setTextColor(Ui.TEXT);
        eventVibrationSwitch.setTextSize(13);
        eventVibrationSwitch.setChecked(draft.alertVibration);
        customAlertOptions.addView(eventVibrationSwitch, Ui.weightedParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        LinearLayout.LayoutParams customAlertParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        customAlertParams.setMargins(0, Ui.dp(activity, 3), 0, Ui.dp(activity, 3));
        content.addView(customAlertOptions, customAlertParams);

        reminderRow = Ui.horizontal(activity);
        reminderToday = reminder("当天", 0);
        reminderOne = reminder("提前1天", 1);
        reminderSeven = reminder("提前7天", 7);
        reminderRow.addView(reminderToday, Ui.weightedParams(Ui.dp(activity, 44), 1));
        LinearLayout.LayoutParams reminderOneParams = Ui.weightedParams(Ui.dp(activity, 44), 1);
        reminderOneParams.leftMargin = Ui.dp(activity, 7);
        reminderRow.addView(reminderOne, reminderOneParams);
        LinearLayout.LayoutParams reminderSevenParams = Ui.weightedParams(Ui.dp(activity, 44), 1);
        reminderSevenParams.leftMargin = Ui.dp(activity, 7);
        reminderRow.addView(reminderSeven, reminderSevenParams);
        addField(content, "提醒日期（可多选）", reminderRow, Ui.dp(activity, 44));

        reminderTimeButton = Ui.button(
                activity,
                ReminderTime.format(draft.reminderHour),
                Ui.TEXT,
                Ui.SURFACE,
                15
        );
        reminderTimeButton.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        reminderTimeButton.setOnClickListener(view -> showReminderHourPicker());
        addField(content, "提醒时间（整点）", reminderTimeButton, Ui.dp(activity, 52));

        reminderOffHint = Ui.text(
                activity,
                "关闭后仍保留日期，但不会发送通知",
                12,
                Ui.MUTED,
                false
        );
        LinearLayout.LayoutParams reminderOffHintParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        reminderOffHintParams.setMargins(
                Ui.dp(activity, 4),
                Ui.dp(activity, 7),
                Ui.dp(activity, 4),
                0
        );
        content.addView(reminderOffHint, reminderOffHintParams);

        noteInput = new EditText(activity);
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteInput.setMinLines(2);
        noteInput.setMaxLines(4);
        noteInput.setGravity(android.view.Gravity.TOP);
        noteInput.setText(draft.note);
        noteInput.setHint("写点要记住的事情");
        noteInput.setTextColor(Ui.TEXT);
        noteInput.setHintTextColor(Ui.MUTED);
        noteInput.setTextSize(14);
        noteInput.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        noteInput.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 15, Ui.BORDER, 1));
        addField(content, "备注（可选）", noteInput, Ui.dp(activity, 82));

        TextView export = Ui.button(activity, "添加到本机日历", Ui.ACCENT, Color.rgb(252, 233, 229), 15);
        LinearLayout.LayoutParams exportParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, 48)
        );
        exportParams.setMargins(0, Ui.dp(activity, 18), 0, 0);
        content.addView(export, exportParams);

        LinearLayout actions = Ui.horizontal(activity);
        TextView deleteButton = null;
        if (editing) {
            deleteButton = Ui.button(
                    activity,
                    "删除",
                    Ui.ACCENT,
                    Color.rgb(252, 239, 236),
                    15
            );
            actions.addView(deleteButton, Ui.weightedParams(Ui.dp(activity, 48), 0.9f));
        }
        TextView cancelButton = Ui.button(
                activity,
                "取消",
                Ui.TEXT,
                Color.rgb(239, 237, 231),
                15
        );
        LinearLayout.LayoutParams cancelParams = Ui.weightedParams(Ui.dp(activity, 48), 1);
        if (editing) cancelParams.leftMargin = Ui.dp(activity, 9);
        actions.addView(cancelButton, cancelParams);
        TextView saveButton = Ui.button(activity, "保存", Color.WHITE, Ui.ACCENT, 15);
        LinearLayout.LayoutParams saveParams = Ui.weightedParams(Ui.dp(activity, 48), 1.2f);
        saveParams.leftMargin = Ui.dp(activity, 9);
        actions.addView(saveButton, saveParams);
        LinearLayout.LayoutParams actionParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = Ui.dp(activity, 11);
        content.addView(actions, actionParams);

        calendarGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean lunar = checkedId == lunarRadio.getId();
            solarRadio.setBackground(Ui.ripple(activity, lunar ? Color.TRANSPARENT : Ui.SURFACE, 12));
            lunarRadio.setBackground(Ui.ripple(activity, lunar ? Ui.SURFACE : Color.TRANSPARENT, 12));
            String target = lunar ? DateEvent.CALENDAR_LUNAR : DateEvent.CALENDAR_SOLAR;
            if (!target.equals(draft.calendarType)) {
                try {
                    DateCalculator.convertCalendar(draft, target, LocalDate.now());
                } catch (RuntimeException error) {
                    Toast.makeText(activity, "无法换算当前日期，请重新选择", Toast.LENGTH_SHORT).show();
                    draft.calendarType = target;
                    if (!lunar) draft.leapMonth = false;
                }
            }
            leapCheck.setChecked(draft.leapMonth);
            updateDateUi();
        });
        leapCheck.setOnCheckedChangeListener((button, checked) -> {
            draft.leapMonth = checked;
            updateDateUi();
        });
        yearKnownSwitch.setOnCheckedChangeListener((button, checked) -> {
            draft.yearKnown = checked;
            updateDateUi();
        });
        yearlySwitch.setOnCheckedChangeListener((button, checked) -> {
            draft.yearly = checked;
            updateDateUi();
        });
        reminderEnabledSwitch.setOnCheckedChangeListener((button, checked) -> updateReminderUi());
        followSystemAlertSwitch.setOnCheckedChangeListener((button, checked) -> updateReminderUi());
        dateButton.setOnClickListener(view -> pickDate());
        export.setOnClickListener(view -> {
            if (populateDraft()) {
                exportToCalendar();
            }
        });
        updateReminderUi();
        updateDateUi();

        cancelButton.setOnClickListener(view -> activity.finishAfterTransition());
        saveButton.setOnClickListener(view -> {
            if (populateDraft()) {
                listener.onSaved(draft.copy());
                activity.finishAfterTransition();
            }
        });
        if (deleteButton != null) {
            deleteButton.setOnClickListener(view -> confirmDeletePage());
        }
        activity.setContentView(scroll);
    }

    private void addField(LinearLayout content, String label, View field, int height) {
        TextView labelView = Ui.text(activity, label, 12, Ui.MUTED, true);
        LinearLayout.LayoutParams labelParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(Ui.dp(activity, 2), Ui.dp(activity, 16), 0, Ui.dp(activity, 8));
        content.addView(labelView, labelParams);
        content.addView(field, Ui.linearParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
    }

    private RadioButton radio(String text, boolean checked) {
        RadioButton button = new RadioButton(activity);
        button.setText(text);
        button.setTextColor(Ui.TEXT);
        button.setTextSize(14);
        button.setGravity(android.view.Gravity.CENTER);
        button.setId(View.generateViewId());
        button.setButtonDrawable(null);
        button.setChecked(checked);
        button.setBackground(Ui.ripple(activity, checked ? Ui.SURFACE : Color.TRANSPARENT, 12));
        return button;
    }

    private CheckBox reminder(String label, int offset) {
        CheckBox box = new CheckBox(activity);
        box.setButtonDrawable(null);
        box.setGravity(android.view.Gravity.CENTER);
        box.setTextColor(activity.getColorStateList(R.color.reminder_choice_text));
        box.setTextSize(13);
        box.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        box.setBackgroundResource(R.drawable.reminder_choice_background);
        box.setPadding(Ui.dp(activity, 7), 0, Ui.dp(activity, 7), 0);
        box.setChecked(draft.reminderDays.contains(offset));
        box.setText(activity.getString(
                box.isChecked() ? R.string.reminder_choice_checked : R.string.reminder_choice_unchecked,
                label
        ));
        box.setOnCheckedChangeListener((button, checked) ->
                button.setText(activity.getString(
                        checked ? R.string.reminder_choice_checked : R.string.reminder_choice_unchecked,
                        label
                )));
        return box;
    }

    private HorizontalScrollView createTypeSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout choices = Ui.horizontal(activity);
        typeChips = new TextView[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            final int index = i;
            TextView chip = Ui.text(activity, tags.get(i).name, 13, Ui.TEXT, true);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(view -> {
                selectedTagIndex = index;
                refreshTypeChips();
            });
            LinearLayout.LayoutParams chipParams = Ui.linearParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.dp(activity, 40)
            );
            if (i < tags.size() - 1) chipParams.rightMargin = Ui.dp(activity, 8);
            choices.addView(chip, chipParams);
            typeChips[i] = chip;
        }
        refreshTypeChips();
        scroll.addView(choices, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        scroll.post(() -> {
            TextView selected = typeChips[selectedTagIndex];
            scroll.scrollTo(Math.max(0, selected.getLeft() - Ui.dp(activity, 12)), 0);
        });
        return scroll;
    }

    private void refreshTypeChips() {
        if (typeChips == null) return;
        for (int i = 0; i < typeChips.length; i++) {
            boolean selected = i == selectedTagIndex;
            TextView chip = typeChips[i];
            chip.setTextColor(selected ? Color.WHITE : Ui.TEXT);
            chip.setBackground(Ui.roundedStroke(
                    activity,
                    selected ? Ui.ACCENT : Ui.SURFACE,
                    15,
                    selected ? Ui.ACCENT : Ui.BORDER,
                    1
            ));
        }
    }

    private void showReminderHourPicker() {
        LinearLayout box = Ui.vertical(activity);
        int side = Ui.dp(activity, 24);
        box.setPadding(side, Ui.dp(activity, 8), side, 0);
        NumberPicker hourPicker = numberPicker(0, 23, draft.reminderHour);
        String[] hours = new String[24];
        for (int hour = 0; hour < hours.length; hour++) {
            hours[hour] = ReminderTime.format(hour);
        }
        hourPicker.setDisplayedValues(hours);
        box.addView(hourPicker, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, 148)
        ));

        NianriPickerDialog.show(
                activity,
                "选择提醒时间",
                "所有已选的提醒日期都会在这个整点提醒",
                box,
                () -> {
                    draft.reminderHour = hourPicker.getValue();
                    reminderTimeButton.setText(ReminderTime.format(draft.reminderHour));
                    return true;
                }
        );
    }

    private void pickDate() {
        if (DateEvent.CALENDAR_SOLAR.equals(draft.calendarType)) {
            showSolarPicker();
            return;
        }
        showLunarPicker();
    }

    private void showSolarPicker() {
        LinearLayout box = Ui.horizontal(activity);
        int side = Ui.dp(activity, 5);
        box.setPadding(side, 0, side, 0);
        NumberPicker yearPicker = draft.yearKnown
                ? numberPicker(DateCalculator.MIN_YEAR, DateCalculator.MAX_YEAR, draft.year)
                : null;
        NumberPicker monthPicker = numberPicker(1, 12, draft.month);
        NumberPicker dayPicker = numberPicker(
                1,
                daysInSolarMonth(yearPicker == null ? draft.year : yearPicker.getValue(), draft.month),
                draft.day
        );
        NumberPicker.OnValueChangeListener updateDays = (picker, oldValue, newValue) -> {
            int year = yearPicker == null ? draft.year : yearPicker.getValue();
            int month = monthPicker.getValue();
            int currentDay = dayPicker.getValue();
            dayPicker.setMaxValue(daysInSolarMonth(year, month));
            dayPicker.setValue(Math.min(currentDay, dayPicker.getMaxValue()));
        };
        monthPicker.setOnValueChangedListener((picker, oldValue, newValue) -> {
            updateDays.onValueChange(picker, oldValue, newValue);
        });
        if (yearPicker != null) {
            yearPicker.setOnValueChangedListener(updateDays);
            box.addView(yearPicker, Ui.weightedParams(Ui.dp(activity, 148), 1.25f));
        }
        box.addView(monthPicker, Ui.weightedParams(Ui.dp(activity, 148), 1));
        box.addView(dayPicker, Ui.weightedParams(Ui.dp(activity, 148), 1));

        NianriPickerDialog.show(
                activity,
                draft.yearKnown ? "选择公历日期" : "选择公历月、日",
                draft.yearKnown ? "上下滑动轮盘选择年、月、日" : "上下滑动轮盘选择月、日",
                box,
                () -> {
                    if (yearPicker != null) draft.year = yearPicker.getValue();
                    draft.month = monthPicker.getValue();
                    draft.day = dayPicker.getValue();
                    updateDateUi();
                    return true;
                }
        );
    }

    private void showLunarPicker() {
        LinearLayout box = Ui.vertical(activity);
        int side = Ui.dp(activity, 18);
        box.setPadding(side, Ui.dp(activity, 8), side, 0);
        LinearLayout row = Ui.horizontal(activity);
        NumberPicker yearPicker = draft.yearKnown
                ? numberPicker(DateCalculator.MIN_YEAR, DateCalculator.MAX_YEAR, draft.year)
                : null;
        NumberPicker monthPicker = numberPicker(1, 12, draft.month);
        monthPicker.setDisplayedValues(LUNAR_MONTHS);
        NumberPicker dayPicker = numberPicker(1, 30, draft.day);
        dayPicker.setDisplayedValues(LUNAR_DAYS);
        if (yearPicker != null) {
            row.addView(yearPicker, Ui.weightedParams(Ui.dp(activity, 148), 1.25f));
        }
        row.addView(monthPicker, Ui.weightedParams(Ui.dp(activity, 148), 1));
        row.addView(dayPicker, Ui.weightedParams(Ui.dp(activity, 148), 1));
        box.addView(row, Ui.linearParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 154)));
        Switch leap = new Switch(activity);
        leap.setText("这是闰月日期");
        leap.setChecked(draft.leapMonth);
        leap.setTextColor(Ui.TEXT);
        leap.setTextSize(14);
        leap.setPadding(Ui.dp(activity, 2), 0, Ui.dp(activity, 2), 0);
        box.addView(leap);

        NianriPickerDialog.show(
                activity,
                "选择农历日期",
                draft.yearKnown ? "选择农历年、月、日；闰月可在下方开启" : "选择农历月、日；闰月可在下方开启",
                box,
                () -> {
                    int year = yearPicker == null ? draft.year : yearPicker.getValue();
                    int month = monthPicker.getValue();
                    int day = dayPicker.getValue();
                    boolean isLeap = leap.isChecked();
                    if (draft.yearKnown && !DateCalculator.isValidLunarDate(year, month, day, isLeap)) {
                        int leapMonth = DateCalculator.leapMonthOf(year);
                        String extra = isLeap
                                ? (leapMonth == 0 ? year + "年没有闰月" : year + "年闰" + LUNAR_MONTHS[leapMonth - 1] + "月")
                                : "该农历日期不存在";
                        Toast.makeText(activity, extra, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    draft.year = year;
                    draft.month = month;
                    draft.day = day;
                    draft.leapMonth = isLeap;
                    leapCheck.setChecked(isLeap);
                    updateDateUi();
                    return true;
                }
        );
    }

    private NumberPicker numberPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(activity);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setWrapSelectorWheel(false);
        return picker;
    }

    private int daysInSolarMonth(int year, int month) {
        try {
            return java.time.YearMonth.of(year, month).lengthOfMonth();
        } catch (RuntimeException ignored) {
            if (month == 2) return 29;
            if (month == 4 || month == 6 || month == 9 || month == 11) return 30;
            return 31;
        }
    }

    private void updateDateUi() {
        boolean lunar = DateEvent.CALENDAR_LUNAR.equals(draft.calendarType);
        leapCheck.setVisibility(lunar ? View.VISIBLE : View.GONE);
        boolean yearKnown = yearKnownSwitch == null ? draft.yearKnown : yearKnownSwitch.isChecked();
        boolean yearly = yearlySwitch == null ? draft.yearly : yearlySwitch.isChecked();
        if (!yearKnown) {
            if (lunar) {
                dateButton.setText(String.format(
                        Locale.CHINA,
                        "农历 %s%s月%s",
                        draft.leapMonth ? "闰" : "",
                        LUNAR_MONTHS[draft.month - 1],
                        LUNAR_DAYS[draft.day - 1]
                ));
            } else {
                dateButton.setText(String.format(Locale.CHINA, "公历 %d月%d日", draft.month, draft.day));
            }
            dateHint.setText(yearly
                    ? "未记录年份 · 每年按月、日自动提醒"
                    : "未记录年份 · 将按下一次出现的日期提醒一次");
            return;
        }
        if (!lunar) {
            LocalDate date;
            try {
                date = LocalDate.of(draft.year, draft.month, draft.day);
            } catch (RuntimeException ignored) {
                date = LocalDate.now();
            }
            dateButton.setText(String.format(Locale.CHINA, "公历 %d年%d月%d日", date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
            dateHint.setText(String.format(Locale.CHINA, "对应%s", DateCalculator.lunarText(date)));
            return;
        }
        String leap = draft.leapMonth ? "闰" : "";
        dateButton.setText(String.format(
                Locale.CHINA,
                "农历 %d年%s%s月%s",
                draft.year,
                leap,
                LUNAR_MONTHS[draft.month - 1],
                LUNAR_DAYS[draft.day - 1]
        ));
        try {
            LocalDate solar = DateCalculator.lunarToSolar(draft.year, draft.month, draft.day, draft.leapMonth, true);
            dateHint.setText(String.format(Locale.CHINA, "对应公历 %s", DateCalculator.fullSolarText(solar)));
        } catch (RuntimeException error) {
            dateHint.setText("当前日期无对应公历，请重新选择");
        }
    }

    private void updateReminderUi() {
        boolean enabled = reminderEnabledSwitch == null
                ? draft.reminderEnabled
                : reminderEnabledSwitch.isChecked();
        boolean followsSystem = followSystemAlertSwitch == null
                ? draft.followSystemAlert
                : followSystemAlertSwitch.isChecked();
        followSystemAlertSwitch.setEnabled(enabled);
        followSystemAlertSwitch.setAlpha(enabled ? 1f : 0.42f);
        customAlertOptions.setVisibility(enabled && !followsSystem ? View.VISIBLE : View.GONE);
        eventSoundSwitch.setEnabled(enabled && !followsSystem);
        eventVibrationSwitch.setEnabled(enabled && !followsSystem);
        reminderToday.setEnabled(enabled);
        reminderOne.setEnabled(enabled);
        reminderSeven.setEnabled(enabled);
        reminderTimeButton.setEnabled(enabled);
        reminderRow.setAlpha(enabled ? 1f : 0.42f);
        reminderTimeButton.setAlpha(enabled ? 1f : 0.42f);
        reminderOffHint.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private boolean populateDraft() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("请输入日期名称");
            return false;
        }
        draft.yearKnown = yearKnownSwitch.isChecked();
        if (DateEvent.CALENDAR_LUNAR.equals(draft.calendarType)
                && draft.yearKnown
                && !DateCalculator.isValidLunarDate(draft.year, draft.month, draft.day, draft.leapMonth)) {
            Toast.makeText(activity, "请选择有效的农历日期", Toast.LENGTH_SHORT).show();
            return false;
        }
        draft.title = title;
        TagStore.Tag selectedTag = tags.get(selectedTagIndex);
        draft.tagId = selectedTag.id;
        draft.type = selectedTag.name;
        draft.yearly = yearlySwitch.isChecked();
        draft.reminderEnabled = reminderEnabledSwitch.isChecked();
        draft.followSystemAlert = followSystemAlertSwitch.isChecked();
        draft.alertSound = eventSoundSwitch.isChecked();
        draft.alertVibration = eventVibrationSwitch.isChecked();
        DateCalculator.anchorOneTimeDateWithoutYear(draft, LocalDate.now());
        draft.note = noteInput.getText().toString().trim();
        draft.reminderHour = ReminderTime.normalizeHour(draft.reminderHour);
        draft.reminderDays.clear();
        if (reminderToday.isChecked()) draft.reminderDays.add(0);
        if (reminderOne.isChecked()) draft.reminderDays.add(1);
        if (reminderSeven.isChecked()) draft.reminderDays.add(7);
        if (draft.reminderDays.isEmpty()) {
            draft.reminderDays.add(0);
        }
        if (draft.id == 0) {
            draft.id = System.currentTimeMillis();
            draft.createdAt = draft.id;
        }
        return true;
    }

    private void exportToCalendar() {
        Occurrence occurrence;
        try {
            occurrence = DateCalculator.occurrence(draft, LocalDate.now());
        } catch (RuntimeException error) {
            Toast.makeText(activity, "无法换算这个日期", Toast.LENGTH_SHORT).show();
            return;
        }
        Calendar start = Calendar.getInstance();
        start.set(
                occurrence.solarDate.getYear(),
                occurrence.solarDate.getMonthValue() - 1,
                occurrence.solarDate.getDayOfMonth(),
                draft.reminderHour,
                0,
                0
        );
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.getTimeInMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.getTimeInMillis() + 60 * 60 * 1000L)
                .putExtra(CalendarContract.Events.TITLE, draft.title)
                .putExtra(CalendarContract.Events.DESCRIPTION,
                        "由念日管理 · " + occurrence.primaryDate + " · " + occurrence.secondaryDate
                                + (draft.note.isEmpty() ? "" : "\n" + draft.note));
        if (draft.yearly && DateEvent.CALENDAR_SOLAR.equals(draft.calendarType)) {
            intent.putExtra(CalendarContract.Events.RRULE, "FREQ=YEARLY");
        }
        activity.startActivity(intent);
    }

    private void confirmDeletePage() {
        NianriConfirmDialog.show(
                activity,
                "将“" + draft.title + "”移到回收站？",
                "移到回收站后仍可恢复；这个日期的提醒将停止。",
                "移到回收站",
                () -> {
                    listener.onDeleted(draft.copy());
                    activity.finishAfterTransition();
                }
        );
    }

    private int tagIndex(String tagId) {
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).id.equals(tagId)) return i;
        }
        return 0;
    }

    private static DateEvent createDefault() {
        LocalDate date = LocalDate.now().plusDays(1);
        DateEvent event = new DateEvent();
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.year = date.getYear();
        event.yearKnown = false;
        event.month = date.getMonthValue();
        event.day = date.getDayOfMonth();
        event.yearly = true;
        event.type = DateEvent.TYPE_BIRTHDAY;
        event.tagId = TagStore.TAG_BIRTHDAY;
        return event;
    }
}
