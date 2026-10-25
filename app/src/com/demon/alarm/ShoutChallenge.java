package com.demon.alarm;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

/** Shout at the phone, loud and long enough. Offline: we only read the mic amplitude. */
public class ShoutChallenge extends Challenge {
    private static final int INTERVAL_MS = 120;
    private static final int NEED_MS = 1200;
    private static final int THRESHOLD = 14000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaRecorder recorder;
    private File tempFile;
    private ProgressBar bar;
    private TextView desc;
    private int held;
    private boolean polling;
    private boolean done;

    public ShoutChallenge(Context c, int level) {
        super(c, level);
    }

    @Override
    public String title() {
        return "对着手机大喊";
    }

    @Override
    public View createView() {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            MathChallenge fallback = new MathChallenge(ctx, level);
            fallback.setOnSolved(() -> solved());
            return fallback.createView();
        }
        if (!startRecorder()) {
            MathChallenge fallback = new MathChallenge(ctx, level);
            fallback.setOnSolved(() -> solved());
            return fallback.createView();
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(UI.dp(ctx, 4), UI.dp(ctx, 10), UI.dp(ctx, 4), UI.dp(ctx, 10));

        TextView big = UI.text(ctx, "啊——！", 46, UI.RED);
        big.setGravity(Gravity.CENTER);
        big.setPadding(0, UI.dp(ctx, 16), 0, UI.dp(ctx, 6));
        root.addView(big);

        TextView tip = UI.text(ctx, "对着手机连续大喊约 " + (NEED_MS / 1000) + " 秒", 15, UI.DIM);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UI.dp(ctx, 12));
        lp.topMargin = UI.dp(ctx, 20);
        root.addView(bar, lp);

        desc = UI.text(ctx, "喊出来", 15, UI.DIM);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, UI.dp(ctx, 12), 0, 0);
        root.addView(desc);

        polling = true;
        handler.postDelayed(pollTask, INTERVAL_MS);
        return root;
    }

    private boolean startRecorder() {
        try {
            File dir = ctx.getCacheDir();
            tempFile = new File(dir, "demon_shout_" + System.currentTimeMillis() + ".3gp");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(tempFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            return true;
        } catch (Exception e) {
            stopRecorder();
            return false;
        }
    }

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!polling || done) return;
            int amp = 0;
            try {
                if (recorder != null) amp = recorder.getMaxAmplitude();
            } catch (Exception ignore) {
            }
            if (amp >= THRESHOLD) {
                held += INTERVAL_MS;
            } else {
                held = Math.max(0, held - INTERVAL_MS / 2);
            }
            if (bar != null) bar.setProgress(Math.min(100, held * 100 / NEED_MS));
            if (desc != null) {
                if (amp < 4000) desc.setText("太小声了，张嘴喊");
                else if (amp < THRESHOLD) desc.setText("再大声一点（音量 " + amp + "）");
                else desc.setText("保持！还需要 " + Math.max(0, (NEED_MS - held)) / 1000.0 + " 秒");
            }
            if (held >= NEED_MS) {
                done = true;
                polling = false;
                stopRecorder();
                solved();
                return;
            }
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    private void stopRecorder() {
        try {
            if (recorder != null) {
                recorder.stop();
            }
        } catch (Exception ignore) {
        }
        try {
            if (recorder != null) recorder.release();
        } catch (Exception ignore) {
        }
        recorder = null;
        try {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        } catch (Exception ignore) {
        }
    }

    @Override
    public void release() {
        done = true;
        polling = false;
        handler.removeCallbacksAndMessages(null);
        stopRecorder();
    }
}
