package com.demon.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Single receiver for: alarm firing, the safety copy of an alarm firing, the watchdog that revives a
 * killed ringing session, and system events that require rescheduling (boot / time change / update).
 */
public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "com.demon.alarm.FIRE";
    public static final String ACTION_WATCHDOG = "com.demon.alarm.WATCHDOG";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_LEVEL_BOOST = "level_boost";
    public static final String EXTRA_BACKUP = "backup";
    public static final int TEST_ALARM_ID = 9000;

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (ACTION_FIRE.equals(action)) {
            int id = intent.getIntExtra(EXTRA_ID, -1);
            if (id < 0) return;
            int boost = intent.getIntExtra(EXTRA_LEVEL_BOOST, 0);
            boolean backup = intent.getBooleanExtra(EXTRA_BACKUP, false);

            // The safety copy only exists to rescue a lost primary alarm. If the primary already put
            // the app into the ringing state - or the alarm is serving a "暂停" right now - there is
            // nothing left to rescue.
            if (backup) {
                if (AlarmStore.getRinging(c) == id) return;
                if (AlarmStore.getSnoozeAt(c) > System.currentTimeMillis()) return;
            }

            AlarmStore.markFired(c, id);

            if (id != TEST_ALARM_ID) {
                Alarm a = AlarmStore.find(c, id);
                if (a == null) return;
                if (a.days == 0) {
                    a.enabled = false;
                    AlarmStore.upsert(c, a);
                } else if (!backup) {
                    // re-arm the next occurrence right away, so nothing can leave the series dead
                    AlarmStore.schedule(c, a);
                }
            }
            startRing(c, id, boost);
        } else if (ACTION_WATCHDOG.equals(action)) {
            int id = AlarmStore.getRinging(c);
            if (id < 0) return;
            startRing(c, id, AlarmStore.getRingingLevel(c));
        } else {
            // BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED / QUICKBOOT_POWERON
            // After a reboot every pending alarm is gone, so all of them have to be armed again.
            AlarmStore.rescheduleAll(c);
            GuardService.refresh(c);
            int id = AlarmStore.getRinging(c);
            if (id >= 0) startRing(c, id, AlarmStore.getRingingLevel(c));
        }
    }

    /**
     * Starts the ring service. Android 12+ may refuse a foreground-service start from the background,
     * so every refusal path falls back to something that still makes noise instead of dying quietly.
     */
    private void startRing(Context c, int id, int boost) {
        Intent svc = new Intent(c, RingService.class);
        svc.setAction(RingService.ACTION_START);
        svc.putExtra(EXTRA_ID, id);
        svc.putExtra(EXTRA_LEVEL_BOOST, boost);
        String err = null;
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
            return;
        } catch (RuntimeException e) {
            err = "startForegroundService failed: " + e.getClass().getSimpleName();
        }
        try {
            c.startService(svc);
            return;
        } catch (RuntimeException e) {
            err = (err == null ? "" : err + " / ") + "startService failed: "
                    + e.getClass().getSimpleName();
        }
        // Both service paths refused: ring through a notification instead of giving up.
        AlarmStore.setLastError(c, err);
        RingService.postEmergency(c, id);
    }
}
