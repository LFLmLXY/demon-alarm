package com.demon.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Foreground service that owns the ringing.
 *
 * "关不掉" design:
 *  - setOngoing(true) notification with NO dismiss action: it cannot be swiped from the status bar,
 *    the only way to end it is completing the challenges in RingActivity (which stops this service);
 *  - the activity is re-launched within ~1.5s whenever it loses focus (home key / back to desktop);
 *  - START_STICKY + a 60s exact AlarmManager watchdog revives the service if the process is killed;
 *  - onTaskRemoved re-launches immediately when the task is swiped out of recents.
 */
public class RingService extends Service {
    public static final String ACTION_START = "com.demon.alarm.RING_START";
    public static final String ACTION_STOP = "com.demon.alarm.RING_STOP";
    public static final String ACTION_POKE = "com.demon.alarm.RING_POKE";
    public static final int NOTIF_ID = 4721;
    public static final int NOTIF_FS_ID = 4722;
    public static final int NOTIF_SNOOZE_ID = 4723;
    private static final String CHANNEL = "demon_ring";
    private static final String CHANNEL_SNOOZE = "demon_snooze";
    private static final String EMERGENCY_CHANNEL = "demon_emergency";

    private static volatile boolean running = false;
    private static Alarm activeAlarm;
    private static int activeBoost = 0;

    private MediaPlayer player;
    private Vibrator vibrator;
    private boolean vibrating = false;
    private long lastVibrateAt = 0;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audio;
    private Handler handler;
    private boolean stopped = true;
    private int tickCount = 0;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (stopped) return;
            tick();
            if (handler != null) handler.postDelayed(this, 1500);
        }
    };

    public static boolean isRunning() {
        return running;
    }

    public static Alarm getActiveAlarm() {
        return activeAlarm;
    }

    public static int getActiveBoost() {
        return activeBoost;
    }

    public static void stopNow(Context c) {
        Intent i = new Intent(c, RingService.class);
        i.setAction(ACTION_STOP);
        try {
            c.startService(i);
        } catch (Exception ignore) {
        }
    }

    /** Ask the service to put the ring screen back on top right now. */
    public static void poke(Context c) {
        if (!running) return;
        Intent i = new Intent(c, RingService.class);
        i.setAction(ACTION_POKE);
        try {
            c.startService(i);
        } catch (Exception ignore) {
        }
    }

    /**
     * Spend one "暂停" token. The alarm is NOT cancelled: it is re-armed a few seconds later,
     * and the next round is harder. The user can never buy more than alarm.snoozeMax pauses.
     */
    public static void snoozeNow(Context c) {
        Alarm a = activeAlarm;
        if (a == null) {
            // service state lost (process death) but the user tapped 暂停: recover from the store
            int id = AlarmStore.getRinging(c);
            if (id >= 0) {
                a = (id == AlarmReceiver.TEST_ALARM_ID) ? AlarmStore.loadTest(c) : AlarmStore.find(c, id);
            }
            if (a == null) return;
        }
        int tier = AlarmStore.getTier(c) + 1;
        AlarmStore.setTier(c, tier);
        int secs = AlarmStore.snoozeSeconds(tier);
        // The pause must not be cut short by the safety copy of the alarm that is being paused.
        AlarmStore.cancelBackup(c, a.id);
        AlarmStore.scheduleSnoozeMs(c, a, secs * 1000L, tier);
        RingActivity.markDismissed();
        notifySnooze(c, a, secs, tier);
        stopNow(c);
    }

    /** The only legitimate way to silence the alarm: the challenge was beaten. */
    public static void dismissNow(Context c) {
        Alarm a = activeAlarm;
        RingActivity.markDismissed();
        AlarmStore.clearRinging(c);
        if (a != null) {
            AlarmStore.cancelSnooze(c, a.id);
            // The safety copy 20 s behind this alarm must not re-ring after the user beat it.
            AlarmStore.cancelBackup(c, a.id);
            if (a.id != AlarmReceiver.TEST_ALARM_ID && a.days == 0) {
                a.enabled = false;
                AlarmStore.upsert(c, a);
            }
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.cancel(NOTIF_FS_ID);
                nm.cancel(NOTIF_SNOOZE_ID);
            } catch (Exception ignore) {
            }
        }
        stopNow(c);
    }

    /** Called when the ring screen is actually visible again: drop the fallback full-screen alert. */
    public static void acknowledgeFullScreen(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.cancel(NOTIF_FS_ID);
        } catch (Exception ignore) {
        }
    }

    /**
     * Last resort when the ring service cannot be started at all: a high-importance alarm
     * notification with sound/vibration and a full-screen intent. It also sets the ringing marker and
     * a short watchdog, so the service gets one more chance to take over 15 s later.
     */
    public static void postEmergency(Context c, int id) {
        try {
            Alarm a = (id == AlarmReceiver.TEST_ALARM_ID) ? AlarmStore.loadTest(c) : AlarmStore.find(c, id);
            if (a == null) a = AlarmStore.loadTest(c);
            AlarmStore.setRinging(c, id, 0);
            AlarmStore.armWatchdog(c, 15000);

            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(EMERGENCY_CHANNEL, "恶魔闹铃 · 紧急响铃",
                    NotificationManager.IMPORTANCE_HIGH);
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (a.wantsSound() && sound != null) {
                ch.setSound(sound, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                ch.setSound(null, null);
            }
            ch.enableVibration(a.wantsVibrate());
            if (a.wantsVibrate()) ch.setVibrationPattern(new long[]{0, 900, 350, 900, 350, 1300, 400});
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);

            Intent open = new Intent(c, RingActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.putExtra(AlarmReceiver.EXTRA_ID, a.id);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(c, 2, open, piFlags);

            Notification n = new Notification.Builder(c, EMERGENCY_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(a.timeText() + "  恶魔闹铃")
                    .setContentText("闹铃正在响 —— 做完起床关卡才会停")
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .build();
            nm.notify(NOTIF_FS_ID, n);
        } catch (Exception ignore) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_POKE.equals(intent.getAction())) {
            if (!stopped) launchActivity();
            return START_STICKY;
        }
        int id = intent != null ? intent.getIntExtra(AlarmReceiver.EXTRA_ID, Integer.MIN_VALUE) : Integer.MIN_VALUE;
        int boost = intent != null ? intent.getIntExtra(AlarmReceiver.EXTRA_LEVEL_BOOST, 0) : 0;
        if (id == Integer.MIN_VALUE) id = AlarmStore.getRinging(this);
        if (boost == 0) boost = AlarmStore.getRingingLevel(this);
        if (id < 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running && activeAlarm != null && activeAlarm.id == id && !stopped) {
            ensureRinging();
            return START_STICKY;
        }
        begin(id, boost);
        return START_STICKY;
    }

    private void begin(int id, int boost) {
        Alarm a = (id == AlarmReceiver.TEST_ALARM_ID) ? AlarmStore.loadTest(this) : AlarmStore.find(this, id);
        if (a == null) a = AlarmStore.loadTest(this);
        activeAlarm = a;
        activeBoost = boost;
        stopped = false;
        running = true;

        // boost == 0 means a brand new ringing session: the pause tokens are handed back.
        if (boost == 0) AlarmStore.setTier(this, 0);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.cancel(NOTIF_SNOOZE_ID);
            } catch (Exception ignore) {
            }
        }

        AlarmStore.setRinging(this, id, boost);
        if (handler == null) handler = new Handler(Looper.getMainLooper());

        startForegroundCompat();
        acquireWakeLock();
        AlarmStore.setLastError(this, "");
        vibrating = false;
        lastVibrateAt = 0;
        if (wantSound()) {
            startAudio();
            raiseVolume();
        }
        if (wantVibrate()) startVibration();
        launchActivity();
        AlarmStore.armWatchdog(this, 60000);
        tickCount = 0;
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 800);
    }

    private void ensureRinging() {
        if (wantSound()) {
            raiseVolume();
            if (player == null) {
                startAudio();
            } else if (!player.isPlaying()) {
                try {
                    player.start();
                } catch (Exception ignore) {
                }
            }
        }
        ensureVibration();
        launchActivity();
        AlarmStore.armWatchdog(this, 60000);
    }

    private boolean wantSound() {
        return activeAlarm == null || activeAlarm.wantsSound();
    }

    private boolean wantVibrate() {
        return activeAlarm == null || activeAlarm.wantsVibrate();
    }

    private void tick() {
        ensureRinging();
        tickCount++;
        if (tickCount % 20 == 0) AlarmStore.armWatchdog(this, 60000);
    }

    // ------------------------------------------------------------------ notification

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "恶魔闹铃 · 正在响铃",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            try {
                nm.createNotificationChannel(ch);
            } catch (Exception ignore) {
            }
        }

        Intent open = new Intent(this, RingActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(AlarmReceiver.EXTRA_ID, activeAlarm.id);
        open.putExtra(AlarmReceiver.EXTRA_LEVEL_BOOST, activeBoost);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL);
        else b = new Notification.Builder(this);

        String title = activeAlarm.timeText() + "  恶魔闹铃";
        if (activeAlarm.label != null && activeAlarm.label.length() > 0) {
            title = title + " · " + activeAlarm.label;
        }
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText("闹铃正在响 —— 没有关闭按钮，做完起床关卡才会停")
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(false)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true);
        if (Build.VERSION.SDK_INT >= 24) {
            b.setColorized(true);
            b.setColor(0xFF4A0A0F);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            // The system refused the foreground notification: still make noise instead of dying quiet.
            AlarmStore.setLastError(this, "响铃通知失败: " + e.getClass().getSimpleName());
            if (activeAlarm != null) postEmergency(this, activeAlarm.id);
        }

    }

    // ------------------------------------------------------------------ audio / vibration

    private void startAudio() {
        if (audio == null) audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int[] types = {RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE,
                RingtoneManager.TYPE_NOTIFICATION};
        for (int t : types) {
            try {
                Uri u = RingtoneManager.getDefaultUri(t);
                if (u == null) continue;
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mp.setDataSource(this, u);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                return;
            } catch (Exception ignore) {
            }
        }
    }

    private void raiseVolume() {
        try {
            if (audio == null) audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            if (audio.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        } catch (Exception ignore) {
        }
    }

    /** 仅震动：几乎连续的震动（间隔只有 90ms）。 */
    private static final long[] PATTERN_ONLY = {0, 900, 90, 900, 90, 1500, 90};
    /** 铃声 + 震动：跟着铃声节奏来，不抢戏。 */
    private static final long[] PATTERN_MIX = {0, 800, 300, 800, 300, 1300, 300};

    private boolean vibrateOnly() {
        return activeAlarm != null && !activeAlarm.wantsSound();
    }

    private void startVibration() {
        try {
            if (vibrator == null) initVibrator();
            if (vibrator == null || !vibrator.hasVibrator()) {
                AlarmStore.setLastError(this, "这台设备没有可用的震动马达");
                return;
            }
            long[] pattern = vibrateOnly() ? PATTERN_ONLY : PATTERN_MIX;
            if (Build.VERSION.SDK_INT >= 26) {
                int amp = Settings.vibrateAmplitude(this);
                int[] amps = new int[pattern.length];
                for (int i = 0; i < amps.length; i++) amps[i] = (i % 2 == 1) ? amp : 0;
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, amps, 0);
                // USAGE_ALARM is the whole trick: the default usage is suppressed by 静音 / 勿扰
                // and by several ROMs once the screen is locked, which is exactly when this alarm
                // has to shake. Routing it through the alarm usage keeps it running on lock screen.
                try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        android.os.VibrationAttributes attr = new android.os.VibrationAttributes.Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                                .build();
                        vibrator.vibrate(effect, attr);
                    } else {
                        AudioAttributes attrs = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build();
                        vibrator.vibrate(effect, attrs);
                    }
                } catch (Throwable t) {
                    vibrator.vibrate(effect);
                }
            } else {
                vibrator.vibrate(pattern, 0);
            }
            vibrating = true;
            lastVibrateAt = SystemClock.elapsedRealtime();
        } catch (Throwable e) {
            AlarmStore.setLastError(this, "震动失败：" + e);
        }
    }

    /**
     * 震动本身是无限循环的，但部分 ROM 会在一段时间后把它掐掉，而 Vibrator.isVibrating() 不是公开 API，
     * 所以按时间戳主动重挂：仅震动模式每 2.5 秒重挂一次（听感上就是一直在震），带铃声时每 20 秒一次。
     */
    private void ensureVibration() {
        if (!wantVibrate()) return;
        long rearm = vibrateOnly() ? 2500L : 20000L;
        if (SystemClock.elapsedRealtime() - lastVibrateAt >= rearm) startVibration();
    }

    private void initVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Object vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm instanceof android.os.VibratorManager) {
                    vibrator = ((android.os.VibratorManager) vm).getDefaultVibrator();
                }
            }
            if (vibrator == null) {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable ignore) {
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            if (wakeLock == null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DemonAlarm::ring");
            wakeLock.setReferenceCounted(false);
            if (!wakeLock.isHeld()) wakeLock.acquire();
        } catch (Exception ignore) {
        }
    }

    // ------------------------------------------------------------------ activity launch

    void launchActivity() {
        if (RingActivity.isAlive() || RingActivity.isDismissed()) return;
        Intent i = new Intent(this, RingActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        i.putExtra(AlarmReceiver.EXTRA_ID, activeAlarm == null ? -1 : activeAlarm.id);
        i.putExtra(AlarmReceiver.EXTRA_LEVEL_BOOST, activeBoost);
        try {
            startActivity(i);
        } catch (Exception ignore) {
        }
        // Android 10+ may silently block background activity starts; the full-screen intent is the
        // documented exemption for alarm apps, so keep re-posting it until the screen is really up.
        forceFullScreen();
    }

    private PendingIntent ringPendingIntent() {
        Intent open = new Intent(this, RingActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(AlarmReceiver.EXTRA_ID, activeAlarm == null ? -1 : activeAlarm.id);
        open.putExtra(AlarmReceiver.EXTRA_LEVEL_BOOST, activeBoost);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 1, open, piFlags);
    }

    private void forceFullScreen() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.cancel(NOTIF_FS_ID);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "恶魔闹铃 · 正在响铃",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.setSound(null, null);
                ch.enableVibration(false);
                ch.setShowBadge(false);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(this, CHANNEL);
            } else {
                b = new Notification.Builder(this);
            }
            b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(activeAlarm == null ? "恶魔闹铃" : activeAlarm.timeText() + " 恶魔闹铃")
                    .setContentText("闹铃正在响，做完起床关卡才会停")
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(ringPendingIntent())
                    .setFullScreenIntent(ringPendingIntent(), true);
            nm.notify(NOTIF_FS_ID, b.build());
        } catch (Exception ignore) {
        }
    }

    private static void notifySnooze(Context c, Alarm a, int secs, int tier) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_SNOOZE, "恶魔闹铃 · 暂停中",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setSound(null, null);
                ch.enableVibration(false);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(c, CHANNEL_SNOOZE);
            } else {
                b = new Notification.Builder(c);
            }
            int left = Math.max(0, a.snoozeMax - tier);
            b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("闹铃暂停 " + secs + " 秒")
                    .setContentText(left > 0 ? "它一定会再响（还剩 " + left + " 次暂停）" : "暂停机会已用完，下一次只能做完关卡")
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setShowWhen(false);
            nm.notify(NOTIF_SNOOZE_ID, b.build());
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (!stopped) {
            // user swiped the app out of recents: come right back
            if (handler != null) handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    launchActivity();
                }
            }, 300);
            AlarmStore.armWatchdog(this, 3000);
        }
    }

    // ------------------------------------------------------------------ shutdown

    private void shutdown() {
        stopped = true;
        running = false;
        activeAlarm = null;
        activeBoost = 0;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        handler = null;
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception ignore) {
        }
        player = null;
        vibrating = false;
        lastVibrateAt = 0;
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignore) {
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignore) {
        }
        AlarmStore.setRinging(this, -1, 0);
        AlarmStore.disarmWatchdog(this);
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_FS_ID);
        } catch (Exception ignore) {
        }
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        } catch (Exception ignore) {
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (!stopped) {
            // killed by the system while ringing: leave the ringing marker set so the watchdog revives us
            running = false;
            if (handler != null) handler.removeCallbacksAndMessages(null);
            handler = null;
            try {
                if (player != null) {
                    player.stop();
                    player.release();
                }
            } catch (Exception ignore) {
            }
            player = null;
            AlarmStore.armWatchdog(this, 5000);
        }
        super.onDestroy();
    }
}
