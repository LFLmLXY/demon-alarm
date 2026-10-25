package com.demon.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps the app alive while any alarm is armed. Two jobs:
 * 1) the ongoing notification proves the app is still watching, and keeps aggressive task cleaners
 *    from treating the process as idle background work;
 * 2) every few minutes it re-arms the schedule, so an alarm that a ROM cleanup dropped comes back.
 */
public class GuardService extends Service {
    static final int NOTIF_ID = 4741;
    static final String CHANNEL = "demon_guard";
    static final String ACTION_REFRESH = "com.demon.alarm.GUARD";
    static final long TICK_MS = 60_000L;
    /** Re-arm the schedule every this many ticks (3 minutes). */
    static final int HEAL_EVERY = 3;

    private Handler handler;
    private int ticks;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!AlarmStore.hasEnabled(GuardService.this)) {
                stopSelf();
                return;
            }
            ticks++;
            if (ticks % HEAL_EVERY == 0) AlarmStore.scheduleAll(GuardService.this);
            putNotification();
            if (handler != null) handler.postDelayed(this, TICK_MS);
        }
    };

    /** Start (or refresh) the guard when at least one alarm is enabled, stop it when none is. */
    public static void refresh(Context c) {
        try {
            if (AlarmStore.hasEnabled(c)) {
                Intent i = new Intent(c, GuardService.class);
                i.setAction(ACTION_REFRESH);
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
                else c.startService(i);
            } else {
                c.stopService(new Intent(c, GuardService.class));
            }
        } catch (RuntimeException ignore) {
            // Background service start refused (some ROMs); alarms themselves stay armed.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AlarmStore.hasEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        putNotification();
        if (handler != null) {
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, TICK_MS);
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away must not stop the watching.
        AlarmStore.scheduleAll(this);
        if (handler != null) {
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, TICK_MS);
        }
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void putNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "闹钟守护",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(ch);

        PendingIntent pi = PendingIntent.getActivity(this, 4101,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | pendingFlags());

        long next = AlarmStore.nextFire(this);
        String text = next > 0
                ? "下一个闹钟 " + AlarmStore.whenText(next) + " · " + AlarmStore.countdownText(next)
                : "点一下新建一个闹钟";

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("恶魔闹铃 · 守护中")
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setColor(0xFFFF453A)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (RuntimeException e) {
            AlarmStore.setLastError(this, "守护通知启动失败: " + e.getClass().getSimpleName());
            // Never leave a started-but-not-foreground service behind: the system would report an ANR.
            stopSelf();
        }
    }

    static int pendingFlags() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
