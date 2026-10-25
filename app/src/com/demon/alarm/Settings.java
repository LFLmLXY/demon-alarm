package com.demon.alarm;

import android.content.Context;
import android.content.SharedPreferences;

/** App-wide preferences (per-alarm options live on Alarm). */
public final class Settings {
    private static final String PREF = "demon_settings";

    public static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** difficulty used for newly created alarms */
    public static int defaultLevel(Context c) {
        return Alarm.clamp(p(c).getInt("default_level", 2), 1, 5);
    }

    public static void setDefaultLevel(Context c, int v) {
        p(c).edit().putInt("default_level", Alarm.clamp(v, 1, 5)).apply();
    }

    /** pause tokens used for newly created alarms */
    public static int defaultSnooze(Context c) {
        return Alarm.clamp(p(c).getInt("default_snooze", 3), 0, 6);
    }

    public static void setDefaultSnooze(Context c, int v) {
        p(c).edit().putInt("default_snooze", Alarm.clamp(v, 0, 6)).apply();
    }

    /** allow the volume keys to silence the ring tone */
    public static boolean allowVolumeSilence(Context c) {
        return p(c).getBoolean("allow_volume", false);
    }

    public static void setAllowVolumeSilence(Context c, boolean v) {
        p(c).edit().putBoolean("allow_volume", v).apply();
    }

    /** vibration strength when ringing: 0 = 标准 60%, 1 = 强 85% (default), 2 = 最强 100% */
    public static int vibrateLevel(Context c) {
        return Alarm.clamp(p(c).getInt("vibrate_level", 1), 0, 2);
    }

    public static void setVibrateLevel(Context c, int v) {
        p(c).edit().putInt("vibrate_level", Alarm.clamp(v, 0, 2)).apply();
    }

    /** the 0-255 amplitude actually requested from the vibrator */
    public static int vibrateAmplitude(Context c) {
        int lv = vibrateLevel(c);
        if (lv == 0) return 153;
        if (lv == 2) return 255;
        return 217;
    }
}
