package com.demon.alarm;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Physically shake the phone N times — you cannot do this while lying down half asleep. */
public class ShakeChallenge extends Challenge implements SensorEventListener {
    private final int target;
    private int count;
    private long lastAt;
    private float lx, ly, lz;
    private boolean hasLast;
    private boolean listening;
    private SensorManager sm;
    private Sensor sensor;
    private TextView counter;
    private ProgressBar bar;

    public ShakeChallenge(Context c, int level) {
        super(c, level);
        this.target = Math.min(180, 20 + level * 15);
    }

    @Override
    public String title() {
        return "摇晃手机 " + target + " 次";
    }

    @Override
    public View createView() {
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        sensor = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            MathChallenge fallback = new MathChallenge(ctx, level);
            fallback.setOnSolved(() -> solved());
            return fallback.createView();
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(UI.dp(ctx, 4), UI.dp(ctx, 10), UI.dp(ctx, 4), UI.dp(ctx, 10));

        TextView tip = UI.text(ctx, "站起来，用力甩动手臂", 15, UI.DIM);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        counter = UI.text(ctx, "0 / " + target, 54, UI.TEXT);
        counter.setGravity(Gravity.CENTER);
        counter.setPadding(0, UI.dp(ctx, 18), 0, UI.dp(ctx, 10));
        root.addView(counter);

        bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(target);
        bar.setProgress(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UI.dp(ctx, 10));
        lp.topMargin = UI.dp(ctx, 8);
        root.addView(bar, lp);

        TextView hint = UI.text(ctx, "手机感受到的甩动次数够了才会停", 13, UI.DIM);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, UI.dp(ctx, 14), 0, 0);
        root.addView(hint);

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                stop();
            }
        });
        return root;
    }

    private void start() {
        if (sm == null || sensor == null) return;
        listening = true;
        count = 0;
        hasLast = false;
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private void stop() {
        listening = false;
        if (sm != null) {
            try {
                sm.unregisterListener(this);
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public void release() {
        stop();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!listening || event == null || event.values == null || event.values.length < 3) return;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float delta = Math.abs(x - lx) + Math.abs(y - ly) + Math.abs(z - lz);
        long now = System.currentTimeMillis();
        if (hasLast && delta > 13f && now - lastAt > 170L) {
            count++;
            lastAt = now;
            if (counter != null) counter.setText(count + " / " + target);
            if (bar != null) bar.setProgress(Math.min(count, target));
            if (count >= target) {
                stop();
                solved();
                return;
            }
        }
        lx = x;
        ly = y;
        lz = z;
        hasLast = true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
