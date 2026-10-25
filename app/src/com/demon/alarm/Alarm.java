package com.demon.alarm;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One alarm. dayMask bit0=Mon .. bit6=Sun; 0 means "只响一次". */
public class Alarm {
    public int id;
    public int hour = 7;
    public int minute = 0;
    public int days = 0x7F;
    public boolean enabled = true;
    public String label = "";
    /** 1..5, drives how nasty the challenges are. */
    public int level = 2;

    public boolean useMath = true;
    public boolean useShake = true;
    public boolean useMemory = false;
    public boolean useType = false;
    public boolean useShout = false;

    /** how many times "再睡 3 分钟" is allowed per ringing session. */
    public int snoozeMax = 1;

    /** how many challenges must be cleared, back to back, before this alarm stops. 1..5. */
    public int chainCount = 2;

    /** 0 = 铃声 + 震动, 1 = 仅铃声, 2 = 仅震动. */
    public int soundMode = 0;

    public static final int SOUND_BOTH = 0;
    public static final int SOUND_ONLY = 1;
    public static final int VIBRATE_ONLY = 2;

    public boolean wantsSound() {
        return soundMode != VIBRATE_ONLY;
    }

    public boolean wantsVibrate() {
        return soundMode != SOUND_ONLY;
    }

    public String soundModeText() {
        if (soundMode == SOUND_ONLY) return "仅铃声";
        if (soundMode == VIBRATE_ONLY) return "仅震动";
        return "铃声+震动";
    }

    public Alarm() {
    }

    public Alarm(int id) {
        this.id = id;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("hour", hour);
            o.put("minute", minute);
            o.put("days", days);
            o.put("enabled", enabled);
            o.put("label", label == null ? "" : label);
            o.put("level", level);
            o.put("useMath", useMath);
            o.put("useShake", useShake);
            o.put("useMemory", useMemory);
            o.put("useType", useType);
            o.put("useShout", useShout);
            o.put("snoozeMax", snoozeMax);
            o.put("soundMode", soundMode);
            o.put("chainCount", chainCount);
        } catch (Exception ignore) {
        }
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        if (o == null) return a;
        a.id = o.optInt("id", 1);
        a.hour = o.optInt("hour", 7);
        a.minute = o.optInt("minute", 0);
        a.days = o.optInt("days", 0x7F);
        a.enabled = o.optBoolean("enabled", true);
        a.label = o.optString("label", "");
        a.level = clamp(o.optInt("level", 2), 1, 5);
        a.useMath = o.optBoolean("useMath", true);
        a.useShake = o.optBoolean("useShake", true);
        a.useMemory = o.optBoolean("useMemory", false);
        a.useType = o.optBoolean("useType", false);
        a.useShout = o.optBoolean("useShout", false);
        a.snoozeMax = clamp(o.optInt("snoozeMax", 1), 0, 6);
        a.soundMode = clamp(o.optInt("soundMode", 0), 0, 2);
        a.chainCount = clamp(o.optInt("chainCount", 2), 1, 5);
        return a;
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public String timeText() {
        return String.format(Locale.CHINA, "%02d:%02d", hour, minute);
    }

    public String daysText() {
        if (days == 0) return "仅一次";
        if (days == 0x7F) return "每天";
        if (days == 0x1F) return "工作日";
        if (days == 0x60) return "周末";
        String[] n = {"一", "二", "三", "四", "五", "六", "日"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) sb.append("周").append(n[i]);
        }
        return sb.toString();
    }

    public List<String> challengeNames() {
        List<String> out = new ArrayList<>();
        if (useShake) out.add("摇晃");
        if (useMath) out.add("算术");
        if (useMemory) out.add("记忆");
        if (useType) out.add("打字");
        if (useShout) out.add("大喊");
        return out;
    }

    public String challengeText() {
        List<String> names = challengeNames();
        if (names.isEmpty()) return "无关卡";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(names.get(i));
        }
        if (chainCount > 1) sb.append(" · 连过").append(chainCount).append("关");
        return sb.toString();
    }

    public boolean hasAnyChallenge() {
        return useMath || useShake || useMemory || useType || useShout;
    }
}
