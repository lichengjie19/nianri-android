package com.nianri.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TagManagerDialog {
    public interface Listener {
        void onChanged();
    }

    private final MainActivity activity;
    private final Listener listener;
    private final TagStore store;
    private LinearLayout tagList;
    private TextView summary;

    public TagManagerDialog(MainActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        store = new TagStore(activity);
    }

    public void show() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = panel();

        TextView title = Ui.text(activity, "管理标签", 20, Ui.TEXT, true);
        panel.addView(title, matchWrap(0));
        summary = Ui.text(activity, "", 12, Ui.MUTED, false);
        panel.addView(summary, matchWrap(7));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        tagList = Ui.vertical(activity);
        scroll.addView(tagList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, 320)
        );
        scrollParams.topMargin = Ui.dp(activity, 18);
        panel.addView(scroll, scrollParams);

        TextView add = Ui.button(activity, "＋  新建标签", Ui.ACCENT, Color.rgb(252, 239, 236), 15);
        add.setOnClickListener(view -> showNameEditor(null));
        panel.addView(add, matchHeight(48, 8));

        TextView done = Ui.button(activity, "完成", Color.WHITE, Ui.ACCENT, 15);
        done.setOnClickListener(view -> dialog.dismiss());
        panel.addView(done, matchHeight(48, 10));

        refresh();
        showStyled(dialog, panel, 540);
    }

    private void refresh() {
        List<TagStore.Tag> tags = store.load();
        Map<String, Integer> counts = new HashMap<>();
        for (DateEvent event : new EventStore(activity).load()) {
            String id = store.resolve(event).id;
            counts.put(id, counts.getOrDefault(id, 0) + 1);
        }
        summary.setText(String.format(
                Locale.CHINA,
                "共 %d 个标签 · “全部”为固定汇总入口",
                tags.size()
        ));
        tagList.removeAllViews();
        for (TagStore.Tag tag : tags) {
            LinearLayout row = tagRow(tag, counts.getOrDefault(tag.id, 0), tags.size() > 1);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = Ui.dp(activity, 9);
            tagList.addView(row, params);
        }
    }

    private LinearLayout tagRow(TagStore.Tag tag, int count, boolean canDelete) {
        LinearLayout row = Ui.horizontal(activity);
        row.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 11), Ui.dp(activity, 10), Ui.dp(activity, 11));
        row.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 17, Ui.BORDER, 1));

        TextView icon = Ui.text(activity, icon(tag), 17, Ui.TEXT, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.rounded(activity, color(tag), 11));
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));

        LinearLayout text = Ui.vertical(activity);
        TextView name = Ui.text(activity, tag.name, 14, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name, matchWrap(0));
        TextView usage = Ui.text(activity, count + " 个日期", 11, Ui.MUTED, false);
        text.addView(usage, matchWrap(5));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.leftMargin = Ui.dp(activity, 11);
        row.addView(text, textParams);

        TextView rename = Ui.button(activity, "改名", Ui.TEXT, Color.rgb(239, 237, 231), 12);
        rename.setTextSize(11);
        rename.setOnClickListener(view -> showNameEditor(tag));
        row.addView(rename, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 36)));

        TextView delete = Ui.button(
                activity,
                "删除",
                canDelete ? Ui.ACCENT : Ui.MUTED,
                canDelete ? Color.rgb(252, 239, 236) : Color.rgb(239, 237, 231),
                12
        );
        delete.setTextSize(11);
        delete.setEnabled(canDelete);
        if (canDelete) delete.setOnClickListener(view -> confirmDelete(tag, count));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 48),
                Ui.dp(activity, 36)
        );
        deleteParams.leftMargin = Ui.dp(activity, 6);
        row.addView(delete, deleteParams);
        return row;
    }

    private void showNameEditor(TagStore.Tag existing) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = panel();
        TextView title = Ui.text(
                activity,
                existing == null ? "新建标签" : "修改标签名称",
                19,
                Ui.TEXT,
                true
        );
        panel.addView(title, matchWrap(0));
        TextView hint = Ui.text(activity, "最多 12 个字，名称不能重复", 12, Ui.MUTED, false);
        panel.addView(hint, matchWrap(7));

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(existing == null ? "" : existing.name);
        input.setSelection(input.getText().length());
        input.setHint("例如：节日、家人、工作");
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setTextSize(15);
        input.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);
        input.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 15, Ui.BORDER, 1));
        panel.addView(input, matchHeight(52, 18));

        LinearLayout actions = Ui.horizontal(activity);
        TextView cancel = Ui.button(activity, "取消", Ui.TEXT, Color.rgb(239, 237, 231), 15);
        TextView save = Ui.button(activity, "保存", Color.WHITE, Ui.ACCENT, 15);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                0,
                Ui.dp(activity, 48),
                1.25f
        );
        saveParams.leftMargin = Ui.dp(activity, 10);
        actions.addView(save, saveParams);
        panel.addView(actions, matchWrap(16));

        cancel.setOnClickListener(view -> dialog.cancel());
        save.setOnClickListener(view -> {
            try {
                if (existing == null) {
                    store.add(input.getText().toString());
                } else {
                    store.rename(existing.id, input.getText().toString());
                }
                listener.onChanged();
                refresh();
                dialog.dismiss();
            } catch (IllegalArgumentException error) {
                input.setError(error.getMessage());
            }
        });
        showStyled(dialog, panel, 440);
        input.requestFocus();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void confirmDelete(TagStore.Tag tag, int count) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = panel();
        TextView title = Ui.text(activity, "删除“" + tag.name + "”？", 19, Ui.TEXT, true);
        panel.addView(title, matchWrap(0));
        String message = count == 0
                ? "删除后不可恢复。"
                : "使用该标签的 " + count + " 个日期会转移到其他可用标签，日期不会被删除。";
        TextView body = Ui.text(activity, message, 13, Ui.MUTED, false);
        body.setLineSpacing(0, 1.2f);
        panel.addView(body, matchWrap(10));

        LinearLayout actions = Ui.horizontal(activity);
        TextView cancel = Ui.button(activity, "取消", Ui.TEXT, Color.rgb(239, 237, 231), 15);
        TextView delete = Ui.button(activity, "删除标签", Color.WHITE, Ui.ACCENT, 15);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0,
                Ui.dp(activity, 48),
                1.25f
        );
        deleteParams.leftMargin = Ui.dp(activity, 10);
        actions.addView(delete, deleteParams);
        panel.addView(actions, matchWrap(18));
        cancel.setOnClickListener(view -> dialog.cancel());
        delete.setOnClickListener(view -> {
            try {
                String replacement = store.delete(tag.id);
                listener.onChanged();
                refresh();
                dialog.dismiss();
                Toast.makeText(
                        activity,
                        count == 0 ? "已删除标签" : "已移至“" + replacement + "”",
                        Toast.LENGTH_SHORT
                ).show();
            } catch (IllegalArgumentException error) {
                Toast.makeText(activity, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        showStyled(dialog, panel, 440);
    }

    private LinearLayout panel() {
        LinearLayout panel = Ui.vertical(activity);
        int side = Ui.dp(activity, 20);
        panel.setPadding(side, Ui.dp(activity, 21), side, Ui.dp(activity, 20));
        panel.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 25, Ui.BORDER, 1));
        return panel;
    }

    private void showStyled(Dialog dialog, View panel, int maxWidthDp) {
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.46f;
        window.setAttributes(attributes);
        int width = Math.min(
                activity.getResources().getDisplayMetrics().widthPixels - Ui.dp(activity, 32),
                Ui.dp(activity, maxWidthDp)
        );
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private String icon(TagStore.Tag tag) {
        if (TagStore.TAG_BIRTHDAY.equals(tag.style)) return "🎂";
        if (TagStore.TAG_ANNIVERSARY.equals(tag.style)) return "💞";
        if (TagStore.TAG_OTHER.equals(tag.style)) return "📌";
        return "🏷";
    }

    private int color(TagStore.Tag tag) {
        if (TagStore.TAG_BIRTHDAY.equals(tag.style)) return Color.rgb(252, 233, 229);
        if (TagStore.TAG_ANNIVERSARY.equals(tag.style)) return Color.rgb(238, 232, 250);
        if (TagStore.TAG_OTHER.equals(tag.style)) return Color.rgb(231, 238, 252);
        int[] palette = {
                Color.rgb(232, 242, 238),
                Color.rgb(249, 239, 222),
                Color.rgb(231, 238, 252),
                Color.rgb(242, 233, 247)
        };
        return palette[Math.floorMod(tag.id.hashCode(), palette.length)];
    }

    private LinearLayout.LayoutParams matchWrap(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Ui.dp(activity, topDp);
        return params;
    }

    private LinearLayout.LayoutParams matchHeight(int heightDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, heightDp)
        );
        params.topMargin = Ui.dp(activity, topDp);
        return params;
    }
}
