package com.demon.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Persistence + AlarmManager scheduling. Everything lives in SharedPreferences so the app works fully offline. */
public final class AlarmStore {
    public static final String PREF = "demon_alarm";
    static final String KEY_LIST = "alarms";
    static final String KEY_RINGING = "ringing_id";
    static final String KEY_RINGING_LEVEL = "ringing_level";
    static final String KEY_TEST = "test_alarm";
    static final String KEY_TIER = "ringing_tier";
    static final String KEY_SNOOZE_AT = "snooze_at";
    static final String KEY_FIRED_AT = "fired_at";
    static final String KEY_FIRED_ID = "fired_id";
    static final String KEY_LAST_ERROR = "last_error";

    private AlarmStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Alarm> load(Context c) {
        List<Alarm> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_LIST, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                try {
                    out.add(Alarm.fromJson(arr.getJSONObject(i)));
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }
        Collections.sort(out, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm a, Alarm b) {
                int x = a.hour * 60 + a.minute;
                int y = b.hour * 60 + b.minute;
                return x < y ? -1 : (x > y ? 1 : 0);
            }
        });
        return out;
    }

    public static void save(Context c, List<Alarm> list) {
        JSONArray arr = new JSONArray();
        for (Alarm a : list) arr.put(a.toJson());
        prefs(c).edit().putString(KEY_LIST, arr.toString()).apply();
    }

    public static Alarm find(Context c, int id) {
        for (Alarm a : load(c)) {
            if (a.id == id) return a;
        }
        return null;
    }

    public static int nextId(Context c) {
        int max = 0;
        for (Alarm a : load(c)) max = Math.max(max, a.id);
        return max + 1;
    }

    public static void upsert(Context c, Alarm a) {
        List<Alarm> list = load(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == a.id) {
                list.set(i, a);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(a);
        save(c, list);
    }

    public static void delete(Context c, int id) {
        List<Alarm> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id == id) list.remove(i);
        }
        save(c, list);
        cancelFire(c, id);
    }

    // ------------------------------------------------------------------ scheduling

    static final long DAY_MS = 24L * 3600L * 1000L;

    /** Strict next trigger. */
    public static long nextTrigger(Alarm a, long now) {
        return nextTrigger(a, now, 0L);
    }

    /**
     * Next trigger time. A positive graceMs lets a time that slipped past by up to graceMs fire right
     * away instead of jumping a whole day - that is what makes "set it for one minute from now" work
     * even when the picked minute is the current one.
     */
    public static long nextTrigger(Alarm a, long now, long graceMs) {
        long base = timeOfDay(a, now);
        if (graceMs > 0 && base <= now && now - base <= graceMs && allowedOn(a, base)) {
            return now + 3000L;
        }
        if (a.days == 0) {
            if (base <= now + 1000L) base += DAY_MS;
            return base;
        }
        for (int add = 0; add < 8; add++) {
            long t = base + add * DAY_MS;
            if (t > now + 1000L && allowedOn(a, t)) return t;
        }
        return base + DAY_MS;
    }

    static long timeOfDay(Alarm a, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        return c.getTimeInMillis();
    }

    /** True when that day is ticked in the alarm (or the alarm is a one-shot). */
    static boolean allowedOn(Alarm a, long millis) {
        if (a.days == 0) return true;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        return (a.days & (1 << bit)) != 0;
    }

    static PendingIntent fireIntent(Context c, int alarmId, int requestCode, int levelBoost, boolean backup) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION_FIRE);
        i.putExtra(AlarmReceiver.EXTRA_ID, alarmId);
        i.putExtra(AlarmReceiver.EXTRA_LEVEL_BOOST, levelBoost);
        if (backup) i.putExtra(AlarmReceiver.EXTRA_BACKUP, true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, requestCode, i, flags);
    }

    static PendingIntent fireIntent(Context c, int alarmId, int requestCode, int levelBoost) {
        return fireIntent(c, alarmId, requestCode, levelBoost, false);
    }

    /** requestCode of the safety copy that fires shortly after the real alarm. */
    static final int BACKUP_REQUEST = 3000;

    /** Seconds the backup fires after the primary. */
    static final long BACKUP_DELAY_MS = 20000L;

    static PendingIntent showIntent(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, 4000, i, flags);
    }

    public static void schedule(Context c, Alarm a) {
        schedule(c, a, 0L);
    }

    /** Arms the real alarm plus a backup copy 20 s later: one lost broadcast cannot swallow a wake-up. */
    public static void schedule(Context c, Alarm a, long graceMs) {
        long now = System.currentTimeMillis();
        long t = nextTrigger(a, now, graceMs);
        arm(c, t, fireIntent(c, a.id, 1000 + a.id, 0, false));
        arm(c, t + BACKUP_DELAY_MS, fireIntent(c, a.id, BACKUP_REQUEST + a.id, 0, true));
    }

    /**
     * setAlarmClock is the strongest wake-up Android offers: it shows the alarm in the status bar and
     * on the lock-screen clock (so the user can see the alarm really is armed), it is never deferred by
     * Doze, and the app gets a short allowlist window so the ring service may start from the background.
     */
    static void arm(Context c, long t, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, showIntent(c)), pi);
            return;
        } catch (SecurityException e) {
            // no exact-alarm permission: fall through to the best effort paths below
        } catch (Throwable ignore) {
            // a few OEM ROMs choke on AlarmClockInfo - do not let that lose the alarm
        }
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, t, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    /** "暂停" is a short taunt: the alarm comes back on its own after delayMs, it is never cancelled. */
    public static void scheduleSnoozeMs(Context c, Alarm a, long delayMs, int levelBoost) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + Math.max(1000L, delayMs);
        prefs(c).edit().putLong(KEY_SNOOZE_AT, at).apply();
        setExact(am, at, fireIntent(c, a.id, 8000 + a.id, levelBoost));
    }

    public static long getSnoozeAt(Context c) {
        return prefs(c).getLong(KEY_SNOOZE_AT, 0L);
    }

    public static void cancelSnooze(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(fireIntent(c, id, 8000 + id, 0));
        prefs(c).edit().remove(KEY_SNOOZE_AT).apply();
    }

    public static void scheduleTest(Context c, int delayMs) {
        arm(c, System.currentTimeMillis() + delayMs, fireIntent(c, 9000, 7000, 0));
    }

    public static void cancelFire(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(fireIntent(c, id, 1000 + id, 0));
        am.cancel(fireIntent(c, id, BACKUP_REQUEST + id, 0, true));
        am.cancel(fireIntent(c, id, 8000 + id, 0));
    }

    /** Cancels only the safety copy, once the real alarm has already woken the app up. */
    static void cancelBackup(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(fireIntent(c, id, BACKUP_REQUEST + id, 0, true));
    }

    static void setExact(AlarmManager am, long t, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, t, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    /** Alarms closer than this are left alone by the self-heal pass: re-arming could push them a day out. */
    static final long IMMINENT_MS = 5L * 60L * 1000L;

    /** Self-heal: re-arms everything that is not about to ring. Used by the guard service and app start. */
    public static void scheduleAll(Context c) {
        long now = System.currentTimeMillis();
        for (Alarm a : load(c)) {
            if (!a.enabled) {
                cancelFire(c, a.id);
                continue;
            }
            if (isWithinGrace(a, now)) continue;
            if (nextTrigger(a, now) - now < IMMINENT_MS) continue;
            schedule(c, a);
        }
    }

    /**
     * True while the picked minute is at most 3 minutes old: such an alarm was just created to ring in a
     * moment, so re-arming it here would silently push it a whole day out.
     */
    static boolean isWithinGrace(Alarm a, long now) {
        long base = timeOfDay(a, now);
        return base <= now && now - base <= 3L * 60L * 1000L;
    }

    /** After a reboot every alarm is gone, so nothing is "imminent" - re-arm all of them. */
    public static void rescheduleAll(Context c) {
        for (Alarm a : load(c)) {
            if (a.enabled) schedule(c, a);
            else cancelFire(c, a.id);
        }
    }

    public static boolean hasEnabled(Context c) {
        for (Alarm a : load(c)) {
            if (a.enabled) return true;
        }
        return false;
    }

    /** Trigger of the nearest enabled alarm, or 0. */
    public static long nextFire(Context c) {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (Alarm a : load(c)) {
            if (a.enabled) best = Math.min(best, nextTrigger(a, now));
        }
        return best == Long.MAX_VALUE ? 0L : best;
    }

    /** "今天 07:00" / "明天 07:00" / "5月3日 07:00". */
    public static String whenText(long t) {
        if (t <= 0) return "—";
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        int diff = dayIndex(c) - dayIndex(now);
        String hm = String.format(java.util.Locale.CHINA, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        if (diff == 0) return "今天 " + hm;
        if (diff == 1) return "明天 " + hm;
        if (diff == 2) return "后天 " + hm;
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日 " + hm;
    }

    static int dayIndex(Calendar c) {
        return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR);
    }

    /** "还有 55 秒" / "还有 3 分钟" / "还有 8 小时". */
    public static String countdownText(long t) {
        long ms = t - System.currentTimeMillis();
        if (ms <= 0) return "马上响";
        long sec = ms / 1000L;
        if (sec < 90) return "还有 " + Math.max(1, sec) + " 秒";
        long min = (sec + 30) / 60;
        if (min < 90) return "还有 " + min + " 分钟";
        return "还有 " + ((min + 30) / 60) + " 小时";
    }

    /** Remembers that this alarm already went off, so its safety copy stays silent. */
    public static void markFired(Context c, int id) {
        prefs(c).edit().putLong(KEY_FIRED_AT, System.currentTimeMillis())
                .putInt(KEY_FIRED_ID, id).apply();
    }

    public static void setLastError(Context c, String msg) {
        prefs(c).edit().putString(KEY_LAST_ERROR, msg == null ? "" : msg).apply();
    }

    public static String getLastError(Context c) {
        return prefs(c).getString(KEY_LAST_ERROR, "");
    }

    // ------------------------------------------------------------------ ringing state

    public static void setRinging(Context c, int id, int levelBoost) {
        prefs(c).edit().putInt(KEY_RINGING, id).putInt(KEY_RINGING_LEVEL, levelBoost).apply();
    }

    public static int getRinging(Context c) {
        return prefs(c).getInt(KEY_RINGING, -1);
    }

    public static int getRingingLevel(Context c) {
        return prefs(c).getInt(KEY_RINGING_LEVEL, 0);
    }

    /** How many "暂停" taunts were already spent in this ringing session. */
    public static int getTier(Context c) {
        return prefs(c).getInt(KEY_TIER, 0);
    }

    public static void setTier(Context c, int tier) {
        prefs(c).edit().putInt(KEY_TIER, Math.max(0, tier)).apply();
    }

    public static void clearRinging(Context c) {
        prefs(c).edit().putInt(KEY_RINGING, -1).putInt(KEY_RINGING_LEVEL, 0)
                .putInt(KEY_TIER, 0).remove(KEY_SNOOZE_AT).apply();
    }

    /** Seconds bought by the n-th "暂停" (n starts at 1). Escalates, then stops growing. */
    public static int snoozeSeconds(int tier) {
        int[] ladder = {10, 20, 40, 60};
        int i = Math.max(1, tier) - 1;
        if (i >= ladder.length) i = ladder.length - 1;
        return ladder[i];
    }

    // ------------------------------------------------------------------ watchdog

    public static PendingIntent watchdogIntent(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION_WATCHDOG);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 7331, i, flags);
    }

    public static void armWatchdog(Context c, long delayMs) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        setExact(am, System.currentTimeMillis() + delayMs, watchdogIntent(c));
    }

    public static void disarmWatchdog(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(watchdogIntent(c));
    }

    // ------------------------------------------------------------------ test alarm

    public static void saveTest(Context c, Alarm a) {
        prefs(c).edit().putString(KEY_TEST, a.toJson().toString()).apply();
    }

    public static Alarm loadTest(Context c) {
        String raw = prefs(c).getString(KEY_TEST, "");
        if (raw == null || raw.isEmpty()) {
            Alarm a = new Alarm(9000);
            a.label = "试响";
            return a;
        }
        try {
            return Alarm.fromJson(new JSONObject(raw));
        } catch (Exception e) {
            Alarm a = new Alarm(9000);
            a.label = "试响";
            return a;
        }
    }
}
