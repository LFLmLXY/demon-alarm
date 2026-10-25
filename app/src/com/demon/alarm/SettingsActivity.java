package com.demon.alarm;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Global preferences + the "why is it like this" page. */
public class SettingsActivity extends Activity {
    private TextView levelValue;
    private TextView snoozeValue;
    private Switch volumeSwitch;
    private int level;
    private int snooze;
    private int vib;
    private TextView[] vibChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        level = Settings.defaultLevel(this);
        snooze = Settings.defaultSnooze(this);
        vib = Settings.vibrateLevel(this);
        buildUi();
        sync();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UI.bg(this));
        root.setPadding(UI.dp(this, 18), UI.dp(this, 26), UI.dp(this, 18), UI.dp(this, 16));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = UI.text(this, "←", 22, UI.TEXT);
        back.setPadding(UI.dp(this, 2), UI.dp(this, 4), UI.dp(this, 14), UI.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);
        TextView title = UI.text(this, "设置", 19, UI.TEXT);
        top.addView(title);
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, UI.dp(this, 16), 0, 0);

        LinearLayout card1 = card();
        card1.addView(section("新闹钟的默认难度（1-5）"));
        levelValue = UI.text(this, "", 22, UI.YELLOW);
        levelValue.setPadding(0, UI.dp(this, 4), 0, UI.dp(this, 4));
        levelValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                level = level >= 5 ? 1 : level + 1;
                Settings.setDefaultLevel(SettingsActivity.this, level);
                sync();
            }
        });
        card1.addView(levelValue);
        card1.addView(section2("点星星切换。高难度会加大数范围、摇晃次数、记忆位数。"));
        content.addView(card1);

        LinearLayout card2 = card();
        card2.addView(section("新闹钟默认暂停次数（0-6）"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepButton("－");
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snooze = Math.max(0, snooze - 1);
                Settings.setDefaultSnooze(SettingsActivity.this, snooze);
                sync();
            }
        });
        row.addView(minus);
        snoozeValue = UI.text(this, "", 15, UI.TEXT);
        snoozeValue.setGravity(Gravity.CENTER);
        row.addView(snoozeValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView plus = stepButton("＋");
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snooze = Math.min(6, snooze + 1);
                Settings.setDefaultSnooze(SettingsActivity.this, snooze);
                sync();
            }
        });
        row.addView(plus);
        card2.addView(row);
        card2.addView(section2("暂停时间按 10 秒 → 20 秒 → 40 秒 → 60 秒 递增，每次都更难。"));
        content.addView(card2);

        LinearLayout cardV = card();
        cardV.addView(section("震动强度"));
        LinearLayout vibRow = new LinearLayout(this);
        vibRow.setOrientation(LinearLayout.HORIZONTAL);
        vibRow.setPadding(0, UI.dp(this, 8), 0, 0);
        String[] vibNames = {"标准", "强", "最强"};
        vibChips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView chip = UI.text(this, vibNames[i], 14, UI.TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 8), UI.dp(this, 10));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vib = idx;
                    Settings.setVibrateLevel(SettingsActivity.this, vib);
                    sync();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = i < 2 ? UI.dp(this, 8) : 0;
            vibRow.addView(chip, lp);
            vibChips[i] = chip;
        }
        cardV.addView(vibRow);
        cardV.addView(section2("标准 60% / 强 85% / 最强 100%（默认「强」）。连续震很久会发热、耗电，"
                + "85% 和 100% 的听感差别很小，对马达寿命也更友好。"));
        content.addView(cardV);

        LinearLayout card3 = card();
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView volTitle = UI.text(this, "允许用音量键静音", 15, UI.TEXT);
        volRow.addView(volTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        volumeSwitch = new Switch(this);
        volumeSwitch.setChecked(Settings.allowVolumeSilence(this));
        volumeSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                Settings.setAllowVolumeSilence(SettingsActivity.this, checked);
            }
        });
        volRow.addView(volumeSwitch);
        card3.addView(volRow);
        card3.addView(section2("默认关闭：响铃时音量键会被吃掉，保证你一定听得见。"));
        content.addView(card3);

        LinearLayout card4 = card();
        card4.addView(section("它是怎么做到关不掉的"));
        String[] lines = {
                "状态栏挂着一条常驻通知，没有关闭按钮，也划不掉。",
                "在想关掉它的界面上，返回键、音量键、Home 键都不好使，离开一秒就自动弹回来。",
                "「暂停」只是买几秒钟：它自己会重新安排下一次响铃。",
                "进程被杀掉也没关系，系统闹钟和重启自启会把它叫回来。",
                "唯一的出口：做完起床关卡。"
        };
        for (String s : lines) {
            TextView t = UI.text(this, "·  " + s, 13, UI.DIM);
            t.setLineSpacing(UI.dp(this, 4), 1f);
            t.setPadding(0, UI.dp(this, 8), 0, 0);
            card4.addView(t);
        }
        content.addView(card4);

        TextView ver = UI.text(this, "恶魔闹铃 1.5 · 完全离线，不联网、不收集任何数据", 11, UI.DIM);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, UI.dp(this, 14), 0, 0);
        content.addView(ver);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(UI.card(this));
        c.setElevation(UI.dp(this, 2));
        c.setPadding(UI.dp(this, 16), UI.dp(this, 14), UI.dp(this, 16), UI.dp(this, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView section(String s) {
        TextView t = UI.text(this, s, 13, UI.TEXT);
        return t;
    }

    private TextView section2(String s) {
        TextView t = UI.text(this, s, 11, UI.DIM);
        t.setLineSpacing(UI.dp(this, 3), 1f);
        t.setPadding(0, UI.dp(this, 6), 0, 0);
        return t;
    }

    private TextView stepButton(String s) {
        TextView t = UI.text(this, s, 20, UI.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(UI.round(UI.CARD2, this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 44));
        t.setLayoutParams(lp);
        return t;
    }

    private void sync() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < level ? "★" : "☆");
        levelValue.setText(sb.toString());
        snoozeValue.setText("暂停 " + snooze + " 次");
        if (vibChips != null) {
            for (int i = 0; i < vibChips.length; i++) {
                if (i == vib) {
                    vibChips[i].setBackground(UI.round(UI.RED, this, 12));
                    vibChips[i].setTextColor(UI.ON_ACCENT);
                } else {
                    vibChips[i].setBackground(UI.chipOff(this));
                    vibChips[i].setTextColor(UI.DIM);
                }
            }
        }
    }
}
