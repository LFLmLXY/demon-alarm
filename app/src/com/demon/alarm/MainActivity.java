package com.demon.alarm;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** Alarm list. Same idea as the system clock app, minus the off switch. */
public class MainActivity extends Activity {
    private LinearLayout bannerBox;
    private LinearLayout listBox;
    private TextView summary;
    private boolean askedNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        AlarmStore.scheduleAll(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        GuardService.refresh(this);
        askNotificationPermission();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UI.bg(this));
        root.setPadding(UI.dp(this, 18), UI.dp(this, 26), UI.dp(this, 18), UI.dp(this, 12));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = UI.text(this, "恶魔闹铃", 26, UI.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.01f);
        titles.addView(title);
        summary = UI.text(this, "", 12, UI.DIM);
        summary.setPadding(0, UI.dp(this, 3), 0, 0);
        titles.addView(summary);
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView gear = UI.text(this, "⚙", 22, UI.DIM);
        gear.setPadding(UI.dp(this, 12), UI.dp(this, 4), UI.dp(this, 12), UI.dp(this, 4));
        gear.setBackground(UI.chipOff(this));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        top.addView(gear);

        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, UI.dp(this, 16), 0, UI.dp(this, 8));

        bannerBox = new LinearLayout(this);
        bannerBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(bannerBox);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        android.widget.Button add = UI.mkButton(this, "＋  新建闹钟", UI.RED, UI.ON_ACCENT);
        add.setTextSize(17);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditActivity.class));
            }
        });
        root.addView(add, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView test = UI.text(this, "先试一次：10 秒后响（完了记得删掉）", 12, UI.DIM);
        test.setGravity(Gravity.CENTER);
        test.setPadding(0, UI.dp(this, 12), 0, UI.dp(this, 2));
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Alarm a = new Alarm(AlarmReceiver.TEST_ALARM_ID);
                a.label = "试响";
                a.level = Settings.defaultLevel(MainActivity.this);
                a.snoozeMax = Settings.defaultSnooze(MainActivity.this);
                a.days = 0;
                AlarmStore.saveTest(MainActivity.this, a);
                AlarmStore.scheduleTest(MainActivity.this, 10000);
                summary.setText("10 秒后响，把手机放下等着");
            }
        });
        root.addView(test);

        setContentView(root);
    }

    // ------------------------------------------------------------------ refresh

    private void refresh() {
        if (listBox == null) return;
        listBox.removeAllViews();
        bannerBox.removeAllViews();

        List<Alarm> alarms = AlarmStore.load(this);
        int on = 0;
        for (Alarm a : alarms) if (a.enabled) on++;
        summary.setText(alarms.isEmpty() ? "还没有闹钟" : (on + " 个已启用 / 共 " + alarms.size() + " 个"));

        fillBanners();

        if (alarms.isEmpty()) {
            addEmptyHint();
        } else {
            for (Alarm a : alarms) listBox.addView(buildRow(a));
            TextView hint = UI.text(this, "点一下修改 · 长按删除", 11, UI.DIM);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, UI.dp(this, 10), 0, 0);
            listBox.addView(hint);
        }
    }

    private void addEmptyHint() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UI.card(this));
        card.setElevation(UI.dp(this, 2));
        card.setPadding(UI.dp(this, 18), UI.dp(this, 18), UI.dp(this, 18), UI.dp(this, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 12);

        TextView t = UI.text(this, "这个闹钟没有「关闭」按钮", 17, UI.RED);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        String[] lines = {
                "响了以后，屏幕会被抢回来，按 Home、返回、音量键都没用。",
                "只有把起床关卡做对（算术 / 摇晃 / 记忆 / 打字 / 大喊），闹铃才会停。",
                "可以点「暂停」，但它会自己再响：10 秒 → 20 秒 → 40 秒，关卡一次比一次难。",
                "真正能关掉它的，只有你下床。"
        };
        for (String s : lines) {
            TextView l = UI.text(this, "·  " + s, 13, UI.DIM);
            l.setLineSpacing(UI.dp(this, 4), 1f);
            l.setPadding(0, UI.dp(this, 10), 0, 0);
            card.addView(l);
        }
        listBox.addView(card, lp);
    }

    private void fillBanners() {
        final String lastError = AlarmStore.getLastError(this);
        if (lastError != null && lastError.length() > 0) {
            banner("⚠ 上次响铃出过问题：" + lastError, "知道了", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlarmStore.setLastError(MainActivity.this, "");
                    refresh();
                }
            });
        }
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31 && am != null) {
            exact = am.canScheduleExactAlarms();
        }
        if (!exact) {
            banner("⚠ 系统没给「精确闹钟」权限，闹钟可能晚几分钟", "去授权", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                }
            });
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            banner("⚠ 电池优化可能杀掉后台闹钟，建议允许后台运行", "去设置", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                }
            });
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            banner("⚠ 没有通知权限：状态栏里的常驻闹铃会看不到", "去开启", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askNotificationPermissionForce();
                }
            });
        }
    }

    private void banner(String text, String action, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(UI.roundStroke(UI.BANNER_BG, UI.BANNER_STROKE, this, 14, 1));
        box.setPadding(UI.dp(this, 12), UI.dp(this, 10), UI.dp(this, 12), UI.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 10);

        TextView t = UI.text(this, text, 12, UI.BANNER_TEXT);
        box.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView btn = UI.text(this, action, 12, UI.BANNER_TEXT);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(UI.dp(this, 10), UI.dp(this, 2), UI.dp(this, 2), UI.dp(this, 2));
        btn.setOnClickListener(click);
        box.addView(btn);

        bannerBox.addView(box, lp);
    }

    private View buildRow(final Alarm a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(UI.card(this));
        card.setElevation(UI.dp(this, 2));
        card.setPadding(UI.dp(this, 16), UI.dp(this, 14), UI.dp(this, 12), UI.dp(this, 14));
        card.setAlpha(a.enabled ? 1f : 0.45f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 10);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView time = UI.text(this, a.timeText(), 32, a.enabled ? UI.TEXT : UI.DIM);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setLetterSpacing(0.02f);
        left.addView(time);

        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < a.level; i++) stars.append("★");
        String sub = a.daysText() + " · " + a.challengeText() + " · " + stars;
        if (a.snoozeMax == 0) sub = sub + " · 不许暂停";
        if (a.soundMode != Alarm.SOUND_BOTH) sub = sub + " · " + a.soundModeText();
        TextView info = UI.text(this, sub, 12, UI.DIM);
        info.setPadding(0, UI.dp(this, 4), 0, 0);
        left.addView(info);

        if (a.enabled) {
            long t = AlarmStore.nextTrigger(a, System.currentTimeMillis());
            TextView next = UI.text(this, "下次 " + AlarmStore.whenText(t) + " · "
                    + AlarmStore.countdownText(t), 12, UI.LINK);
            next.setPadding(0, UI.dp(this, 4), 0, 0);
            left.addView(next);
        }

        if (a.label != null && a.label.length() > 0) {
            TextView label = UI.text(this, a.label, 11, UI.RED);
            label.setPadding(0, UI.dp(this, 3), 0, 0);
            left.addView(label);
        }

        card.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(a.enabled);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Alarm fresh = AlarmStore.find(MainActivity.this, a.id);
                if (fresh == null) return;
                fresh.enabled = isChecked;
                AlarmStore.upsert(MainActivity.this, fresh);
                if (isChecked) AlarmStore.schedule(MainActivity.this, fresh);
                else AlarmStore.cancelFire(MainActivity.this, fresh.id);
                GuardService.refresh(MainActivity.this);
                refresh();
            }
        });
        card.addView(sw);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, EditActivity.class);
                i.putExtra("alarm_id", a.id);
                startActivity(i);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDelete(a);
                return true;
            }
        });
        return card;
    }

    private void confirmDelete(final Alarm a) {
        new AlertDialog.Builder(this)
                .setTitle("删除 " + a.timeText() + " 的闹钟？")
                .setMessage("删掉以后它就不会再叫你了。")
                .setNegativeButton("算了", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        AlarmStore.delete(MainActivity.this, a.id);
                        GuardService.refresh(MainActivity.this);
                        refresh();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ permissions

    private void askNotificationPermission() {
        if (askedNotifications) return;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotifications = true;
            askNotificationPermissionForce();
        }
    }

    private void askNotificationPermissionForce() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }
}
