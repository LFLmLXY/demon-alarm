package com.demon.alarm;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The un-closable ring screen.
 *
 * There is no "关闭闹铃" button anywhere:
 *  - BACK / volume keys are swallowed, there is no menu;
 *  - losing focus (home key, screen off, recents) brings the screen straight back;
 *  - the only exits are (a) finishing the challenge, (b) spending a "暂停" token that
 *    re-schedules the SAME alarm a few seconds later with a harder challenge.
 */
public class RingActivity extends Activity {
    private static volatile boolean alive = false;
    private static volatile boolean dismissed = false;

    public static boolean isAlive() {
        return alive;
    }

    public static boolean isDismissed() {
        return dismissed;
    }

    public static void markDismissed() {
        alive = false;
        dismissed = true;
    }

    /**
     * A new ringing session starts: forget the flag that said "the user already dealt with it".
     * Without this, one 暂停 would poison the process forever - the service refuses to launch the
     * ring screen for any later round because it still thinks the last one was dismissed.
     */
    public static void resetDismissed() {
        dismissed = false;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    private Alarm alarm;
    private int tier;
    private int stageLevel;
    private boolean finalStage;
    private List<Challenge> stages;
    private int stageIndex;
    private Challenge challenge;

    private FrameLayout host;
    private TextView clock;
    private TextView stageLabel;
    private TextView statusText;
    private TextView footHint;
    private Button pauseBtn;
    private boolean finished;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (clock != null) clock.setText(clockFmt.format(new Date()));
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable relaunch = new Runnable() {
        @Override
        public void run() {
            if (dismissed || finished) return;
            try {
                Intent i = new Intent(RingActivity.this, RingActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                if (alarm != null) i.putExtra(AlarmReceiver.EXTRA_ID, alarm.id);
                startActivity(i);
            } catch (Exception ignore) {
            }
            RingService.poke(getApplicationContext());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alive = true;
        dismissed = false;
        setUpWindow();
        build();
        refresh(getIntent());
        handler.postDelayed(clockTick, 1000);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refresh(intent);
    }

    private void setUpWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UI.ringBg(this));
        root.setPadding(UI.dp(this, 20), UI.dp(this, 22), UI.dp(this, 20), UI.dp(this, 18));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = UI.text(this, "恶 魔 闹 铃", 14, UI.RING_ACCENT);
        brand.setLetterSpacing(0.22f);
        top.addView(brand, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        clock = UI.text(this, clockFmt.format(new Date()), 30, UI.RING_TEXT);
        top.addView(clock);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        stageLabel = UI.text(this, "", 21, UI.RING_TEXT);
        stageLabel.setPadding(0, UI.dp(this, 14), 0, UI.dp(this, 4));
        root.addView(stageLabel);

        statusText = UI.text(this, "闹铃正在响 —— 做完下面的关卡才会停", 13, UI.RING_DIM);
        root.addView(statusText);

        host = new FrameLayout(this);
        // 白色答题卡：关卡视图里的文字是深色的，深红背景上必须垫一张白卡才看得清
        host.setBackground(UI.card(this));
        host.setPadding(UI.dp(this, 18), UI.dp(this, 18), UI.dp(this, 18), UI.dp(this, 18));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(host, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        hostLp.topMargin = UI.dp(this, 12);
        root.addView(scroll, hostLp);

        footHint = UI.text(this, "", 12, UI.DIM);
        footHint.setGravity(Gravity.CENTER);
        footHint.setPadding(0, UI.dp(this, 8), 0, UI.dp(this, 8));
        root.addView(footHint);

        pauseBtn = UI.mkButton(this, "暂停", UI.RING_SOFT, UI.RING_TEXT);
        pauseBtn.setBackground(UI.roundStroke(UI.RING_SOFT, UI.RING_LINE, this, 18, 1));
        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPauseAsked();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(pauseBtn, plp);

        setContentView(root);
    }

    private void refresh(Intent intent) {
        if (finished) return;
        int id = intent == null ? -1 : intent.getIntExtra(AlarmReceiver.EXTRA_ID, -1);
        if (id == -1) id = AlarmStore.getRinging(this);
        alarm = (id == AlarmReceiver.TEST_ALARM_ID) ? AlarmStore.loadTest(this) : AlarmStore.find(this, id);
        if (alarm == null) {
            alarm = AlarmStore.loadTest(this);
        }
        tier = AlarmStore.getTier(this);
        finalStage = tier >= alarm.snoozeMax;
        stageLevel = Math.min(5, alarm.level + Math.min(2, tier));

        int count = Math.max(1, Math.min(5, alarm.chainCount));
        if (stages != null) {
            for (Challenge old : stages) {
                try {
                    old.release();
                } catch (Exception ignore) {
                }
            }
            stages = null;
        }
        stages = Challenges.sequence(this, alarm, stageLevel, count);
        stageIndex = 0;
        if (host != null) host.removeAllViews();
        showStage(0);
        if (statusText != null) {
            statusText.setTextColor(UI.RING_DIM);
            statusText.setText(alarm.timeText() + " 的闹铃正在响 · " + (alarm.label == null || alarm.label.isEmpty()
                    ? "没有关闭按钮" : alarm.label));
        }
        updatePauseUi();
    }

    /** Shows stage {@code i} of the queue: one challenge per screen, all of them are mandatory. */
    private void showStage(int i) {
        if (stages == null || i < 0 || i >= stages.size()) return;
        if (challenge != null) {
            try {
                challenge.release();
            } catch (Exception ignore) {
            }
            challenge = null;
        }
        stageIndex = i;
        challenge = stages.get(i);
        challenge.setOnSolved(new Runnable() {
            @Override
            public void run() {
                onStageSolved();
            }
        });
        if (host != null) {
            host.removeAllViews();
            host.addView(challenge.createView());
        }
        if (stageLabel != null) {
            stageLabel.setText("第 " + (i + 1) + " / " + stages.size() + " 关 · " + challenge.title());
        }
    }

    private void onStageSolved() {
        if (finished) return;
        if (stages != null && stageIndex + 1 < stages.size()) {
            final int next = stageIndex + 1;
            if (host != null) {
                host.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!finished) showStage(next);
                    }
                });
            }
            return;
        }
        onSolved();
    }

    private void updatePauseUi() {
        if (pauseBtn == null) return;
        if (finalStage) {
            pauseBtn.setVisibility(View.GONE);
            footHint.setText("暂停机会已经用完 —— 只剩做完关卡这一条路");
            return;
        }
        pauseBtn.setVisibility(View.VISIBLE);
        int left = Math.max(1, alarm.snoozeMax - tier);
        int secs = AlarmStore.snoozeSeconds(tier + 1);
        pauseBtn.setText("暂停 " + secs + " 秒（还剩 " + left + " 次）");
        footHint.setText("暂停后它会自己再响，而且关卡更难 —— 起床才是唯一捷径");
    }

    // ------------------------------------------------------------------ exits

    private void onSolved() {
        if (finished) return;
        finished = true;
        if (challenge != null) {
            try {
                challenge.release();
            } catch (Exception ignore) {
            }
        }
        if (host != null) host.setVisibility(View.GONE);
        if (pauseBtn != null) pauseBtn.setVisibility(View.GONE);
        if (statusText != null) {
            statusText.setTextColor(UI.RING_OK);
            statusText.setText("✔ 起床成功");
        }
        if (stageLabel != null) {
            stageLabel.setTextColor(UI.RING_OK);
            stageLabel.setText("闹铃已停止，今天算你赢");
        }
        if (footHint != null) footHint.setText("干得漂亮，去洗脸吧");
        RingService.dismissNow(getApplicationContext());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishNow();
            }
        }, 800);
    }

    private void onPauseAsked() {
        if (finished) return;
        finished = true;
        int next = tier + 1;
        int secs = AlarmStore.snoozeSeconds(next);
        if (statusText != null) statusText.setText("已暂停 " + secs + " 秒，闹铃会自己再响");
        if (pauseBtn != null) pauseBtn.setEnabled(false);
        if (footHint != null) footHint.setText("别躺回去，它真的会再响");
        if (challenge != null) {
            try {
                challenge.release();
            } catch (Exception ignore) {
            }
        }
        RingService.snoozeNow(getApplicationContext());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishNow();
            }
        }, 350);
    }

    private void finishNow() {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        } catch (Exception ignore) {
            finish();
        }
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------------ un-escapable bits

    @Override
    public void onBackPressed() {
        // 没有返回键：唯一的出口是关卡
        if (statusText != null) statusText.setText("没有返回键，做完关卡就会停");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean volumeKey = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE;
        if (volumeKey && Settings.allowVolumeSilence(this)) {
            return super.dispatchKeyEvent(event);
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE || code == KeyEvent.KEYCODE_BACK
                || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_CAMERA) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        alive = true;
        RingService.acknowledgeFullScreen(getApplicationContext());
        handler.removeCallbacks(relaunch);
    }

    @Override
    protected void onPause() {
        super.onPause();
        alive = false;
        if (!finished && !dismissed) {
            handler.postDelayed(relaunch, 400);
        }
    }

    @Override
    protected void onDestroy() {
        alive = false;
        handler.removeCallbacksAndMessages(null);
        if (challenge != null) {
            try {
                challenge.release();
            } catch (Exception ignore) {
            }
            challenge = null;
        }
        super.onDestroy();
    }
}
