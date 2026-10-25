package com.demon.alarm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

/** Create / edit one alarm. */
public class EditActivity extends Activity {
    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};
    /** A picked time that already slipped past by this much fires right away instead of tomorrow. */
    private static final long GRACE_MS = 3L * 60L * 1000L;

    private int alarmId;
    private Alarm alarm;
    private NumberPicker hourPicker;
    private NumberPicker minutePicker;
    private final TextView[] dayChips = new TextView[7];
    private final TextView[] typeChips = new TextView[5];
    private final TextView[] soundChips = new TextView[3];
    private TextView levelChips;
    private TextView snoozeText;
    private TextView chainText;
    private EditText labelInput;
    private int level;
    private int snooze;
    private int chain;
    private int days;
    private int soundMode;
    private boolean editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alarmId = getIntent().getIntExtra("alarm_id", -1);
        alarm = alarmId > 0 ? AlarmStore.find(this, alarmId) : null;
        editing = alarm != null;
        if (alarm == null) {
            Calendar next = Calendar.getInstance();
            next.add(Calendar.MINUTE, 1);
            alarm = new Alarm(AlarmStore.nextId(this));
            alarm.hour = next.get(Calendar.HOUR_OF_DAY);
            alarm.minute = next.get(Calendar.MINUTE);
            alarm.level = Settings.defaultLevel(this);
            alarm.snoozeMax = Settings.defaultSnooze(this);
            alarm.days = 0x7F;
        }
        level = alarm.level;
        snooze = alarm.snoozeMax;
        chain = alarm.chainCount;
        days = alarm.days;
        soundMode = alarm.soundMode;
        buildUi();
        syncChips();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UI.bg(this));
        root.setPadding(UI.dp(this, 18), UI.dp(this, 26), UI.dp(this, 18), UI.dp(this, 12));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView cancel = UI.text(this, "取消", 15, UI.DIM);
        cancel.setPadding(UI.dp(this, 2), UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(cancel);

        TextView title = UI.text(this, editing ? "修改闹钟" : "新建闹钟", 17, UI.TEXT);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView del = UI.text(this, editing ? "删除" : "", 15, UI.RED);
        del.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 2), UI.dp(this, 8));
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
        top.addView(del);
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, UI.dp(this, 14), 0, UI.dp(this, 16));

        content.addView(buildTimeCard());
        content.addView(buildDayCard());
        content.addView(buildChallengeCard());
        content.addView(buildSoundCard());
        content.addView(buildBehaviorCard());
        content.addView(buildLabelCard());

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button save = UI.mkButton(this, editing ? "保存" : "创建这个恶魔闹钟", UI.RED, UI.ON_ACCENT);
        save.setTextSize(17);
        save.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        root.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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
        TextView t = UI.text(this, s, 12, UI.DIM);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, UI.dp(this, 8));
        return t;
    }

    private View buildTimeCard() {
        LinearLayout c = card();
        c.addView(section("时间"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        hourPicker = new NumberPicker(this);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(alarm.hour);
        hourPicker.setFormatter(TWO_DIGITS);
        hourPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(hourPicker, hp);

        TextView colon = UI.text(this, ":", 30, UI.TEXT);
        colon.setPadding(UI.dp(this, 6), 0, UI.dp(this, 6), UI.dp(this, 10));
        row.addView(colon);

        minutePicker = new NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(alarm.minute);
        minutePicker.setFormatter(TWO_DIGITS);
        minutePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        row.addView(minutePicker, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        c.addView(row);
        return c;
    }

    private static final NumberPicker.Formatter TWO_DIGITS = new NumberPicker.Formatter() {
        @Override
        public String format(int value) {
            return value < 10 ? "0" + value : String.valueOf(value);
        }
    };

    private View buildDayCard() {
        LinearLayout c = card();
        c.addView(section("重复"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView chip = UI.text(this, DAY_NAMES[i], 14, UI.TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = UI.dp(this, i == 6 ? 0 : 5);
            row.addView(chip, lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    days ^= (1 << bit);
                    syncChips();
                }
            });
            dayChips[i] = chip;
        }
        c.addView(row);

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setPadding(0, UI.dp(this, 10), 0, 0);
        presets.addView(preset("每天", 0x7F));
        presets.addView(preset("工作日", 0x1F));
        presets.addView(preset("周末", 0x60));
        presets.addView(preset("一次", 0));
        c.addView(presets);
        return c;
    }

    private TextView preset(String name, final int mask) {
        TextView t = UI.text(this, name, 12, UI.DIM);
        t.setGravity(Gravity.CENTER);
        t.setBackground(UI.chipOff(this));
        t.setPadding(UI.dp(this, 10), UI.dp(this, 7), UI.dp(this, 10), UI.dp(this, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UI.dp(this, 8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                days = mask;
                syncChips();
            }
        });
        return t;
    }

    private View buildChallengeCard() {
        LinearLayout c = card();
        c.addView(section("起床关卡（至少选一个）"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"算术", "摇晃", "记忆", "打字", "大喊"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView chip = UI.text(this, names[i], 13, UI.TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = UI.dp(this, i == 4 ? 0 : 5);
            row.addView(chip, lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleType(idx);
                }
            });
            typeChips[i] = chip;
        }
        c.addView(row);

        TextView levelTitle = UI.text(this, "难度", 12, UI.DIM);
        levelTitle.setPadding(0, UI.dp(this, 16), 0, UI.dp(this, 6));
        c.addView(levelTitle);

        levelChips = UI.text(this, "", 20, UI.YELLOW);
        levelChips.setPadding(0, UI.dp(this, 2), 0, 0);
        levelChips.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                level = level >= 5 ? 1 : level + 1;
                syncChips();
            }
        });
        c.addView(levelChips);

        TextView levelHint = UI.text(this, "点星星切换：难度越高，题目越难、摇晃次数越多", 11, UI.DIM);
        levelHint.setPadding(0, UI.dp(this, 6), 0, 0);
        c.addView(levelHint);
        return c;
    }

    private View buildSoundCard() {
        LinearLayout c = card();
        c.addView(section("响铃方式"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"铃声+震动", "仅铃声", "仅震动"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView chip = UI.text(this, names[i], 13, UI.TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = UI.dp(this, i == 2 ? 0 : 5);
            row.addView(chip, lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    soundMode = idx;
                    syncChips();
                }
            });
            soundChips[i] = chip;
        }
        c.addView(row);

        TextView hint = UI.text(this, "仅震动：不响铃，只靠震动把你弄醒；仅铃声：不震动。",
                11, UI.DIM);
        hint.setPadding(0, UI.dp(this, 8), 0, 0);
        c.addView(hint);
        return c;
    }

    private View buildBehaviorCard() {
        LinearLayout c = card();
        c.addView(section("暂停机会"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView minus = UI.text(this, "－", 22, UI.TEXT);
        minus.setGravity(Gravity.CENTER);
        minus.setBackground(UI.round(UI.CARD2, this, 12));
        minus.setPadding(0, UI.dp(this, 6), 0, UI.dp(this, 6));
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snooze = Math.max(0, snooze - 1);
                syncChips();
            }
        });
        row.addView(minus, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 44)));

        snoozeText = UI.text(this, "", 15, UI.TEXT);
        snoozeText.setGravity(Gravity.CENTER);
        row.addView(snoozeText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView plus = UI.text(this, "＋", 22, UI.TEXT);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(UI.round(UI.CARD2, this, 12));
        plus.setPadding(0, UI.dp(this, 6), 0, UI.dp(this, 6));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snooze = Math.min(6, snooze + 1);
                syncChips();
            }
        });
        row.addView(plus, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 44)));
        c.addView(row);

        TextView hint = UI.text(this, "0 次 = 一响就得起来，没有暂停按钮", 11, UI.DIM);
        hint.setPadding(0, UI.dp(this, 8), 0, 0);
        c.addView(hint);

        // ---- how many challenges have to be cleared in one go -------------------------------
        c.addView(section("一次响铃要连过几关"));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        TextView minus2 = UI.text(this, "－", 22, UI.TEXT);
        minus2.setGravity(Gravity.CENTER);
        minus2.setBackground(UI.round(UI.CARD2, this, 12));
        minus2.setPadding(0, UI.dp(this, 6), 0, UI.dp(this, 6));
        minus2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chain = Math.max(1, chain - 1);
                syncChips();
            }
        });
        row2.addView(minus2, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 44)));

        chainText = UI.text(this, "", 15, UI.TEXT);
        chainText.setGravity(Gravity.CENTER);
        row2.addView(chainText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView plus2 = UI.text(this, "＋", 22, UI.TEXT);
        plus2.setGravity(Gravity.CENTER);
        plus2.setBackground(UI.round(UI.CARD2, this, 12));
        plus2.setPadding(0, UI.dp(this, 6), 0, UI.dp(this, 6));
        plus2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chain = Math.min(5, chain + 1);
                syncChips();
            }
        });
        row2.addView(plus2, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 44)));
        c.addView(row2);

        TextView hint2 = UI.text(this,
                "一关过完马上接着下一关，全部过完闹铃才停。只设 1 关很容易过完又倒回去睡。", 11, UI.DIM);
        hint2.setPadding(0, UI.dp(this, 8), 0, 0);
        c.addView(hint2);
        return c;
    }

    private View buildLabelCard() {
        LinearLayout c = card();
        c.addView(section("备注（可选）"));

        labelInput = new EditText(this);
        labelInput.setText(alarm.label == null ? "" : alarm.label);
        labelInput.setHint("比如：再不起来就迟到了");
        labelInput.setHintTextColor(UI.DIM);
        labelInput.setTextColor(UI.TEXT);
        labelInput.setTextSize(15);
        labelInput.setBackground(UI.roundStroke(UI.CARD2, UI.STROKE, this, 12, 1));
        labelInput.setPadding(UI.dp(this, 12), UI.dp(this, 10), UI.dp(this, 12), UI.dp(this, 10));
        c.addView(labelInput);
        return c;
    }

    // ------------------------------------------------------------------ state sync

    private void toggleType(int idx) {
        switch (idx) {
            case 0:
                alarm.useMath = !alarm.useMath;
                break;
            case 1:
                alarm.useShake = !alarm.useShake;
                break;
            case 2:
                alarm.useMemory = !alarm.useMemory;
                break;
            case 3:
                alarm.useType = !alarm.useType;
                break;
            case 4:
                alarm.useShout = !alarm.useShout;
                break;
            default:
                break;
        }
        if (!alarm.hasAnyChallenge()) {
            alarm.useMath = true;
        }
        syncChips();
    }

    private void syncChips() {
        for (int i = 0; i < 7; i++) {
            boolean on = (days & (1 << i)) != 0;
            dayChips[i].setBackground(on ? UI.round(UI.RED, this, 12) : UI.chipOff(this));
            dayChips[i].setTextColor(on ? UI.ON_ACCENT : UI.DIM);
        }

        boolean[] on = {alarm.useMath, alarm.useShake, alarm.useMemory, alarm.useType, alarm.useShout};
        for (int i = 0; i < 5; i++) {
            typeChips[i].setBackground(on[i] ? UI.round(UI.GREEN_SOFT, this, 12) : UI.chipOff(this));
            typeChips[i].setTextColor(on[i] ? UI.GREEN : UI.DIM);
        }

        for (int i = 0; i < 3; i++) {
            boolean sel = soundMode == i;
            soundChips[i].setBackground(sel ? UI.round(UI.GREEN_SOFT, this, 12) : UI.chipOff(this));
            soundChips[i].setTextColor(sel ? UI.GREEN : UI.DIM);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < level ? "★" : "☆");
        levelChips.setText(sb.toString());

        snoozeText.setText("暂停 " + snooze + " 次");
        if (chainText != null) chainText.setText("连过 " + chain + " 关");
    }

    // ------------------------------------------------------------------ save / delete

    private void save() {
        alarm.hour = hourPicker.getValue();
        alarm.minute = minutePicker.getValue();
        alarm.days = days;
        alarm.level = level;
        alarm.snoozeMax = snooze;
        alarm.soundMode = soundMode;
        alarm.chainCount = chain;
        alarm.label = labelInput.getText().toString().trim();
        alarm.enabled = true;
        AlarmStore.upsert(this, alarm);
        // The grace window is what makes "set it for one minute from now" work even when the picked
        // minute is the minute you are already in (the strict rule would push it to tomorrow).
        AlarmStore.schedule(this, alarm, GRACE_MS);
        GuardService.refresh(this);

        if (alarm.useShout && android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // "大喊" needs the mic: ask once, right where the feature was switched on
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 21);
        }

        long t = AlarmStore.nextTrigger(alarm, System.currentTimeMillis(), GRACE_MS);
        String when = AlarmStore.whenText(t) + " · " + AlarmStore.countdownText(t);
        if (exactOk()) {
            Toast.makeText(this, "闹钟已排：" + when, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("还差一个授权")
                .setMessage("系统没有给「闹钟和提醒」权限，闹钟可能会晚几分钟才响。\n\n"
                        + "下次响铃：" + when
                        + "\n\n点「去开启」允许「闹钟和提醒」，它才会准点响。")
                .setNegativeButton("先这样", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        finish();
                    }
                })
                .setPositiveButton("去开启", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        openExactSettings();
                        finish();
                    }
                })
                .show();
    }

    private boolean exactOk() {
        if (android.os.Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        return am == null || am.canScheduleExactAlarms();
    }

    private void openExactSettings() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignore) {
        }
    }

    private void confirmDelete() {
        if (!editing) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除这个闹钟？")
                .setNegativeButton("算了", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        AlarmStore.delete(EditActivity.this, alarm.id);
                        finish();
                    }
                })
                .show();
    }
}
