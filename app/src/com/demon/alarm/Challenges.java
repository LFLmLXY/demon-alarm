package com.demon.alarm;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Picks which anti-sleep task to throw at the user, never the same one twice in a row. */
public final class Challenges {
    public static final int MATH = 1;
    public static final int SHAKE = 2;
    public static final int MEMORY = 3;
    public static final int TYPE = 4;
    public static final int SHOUT = 5;

    private Challenges() {
    }

    /**
     * Builds the whole queue for one ringing session: {@code count} challenges back to back,
     * never the same type twice in a row, each pair of stages one notch harder than the last.
     */
    public static List<Challenge> sequence(Context c, Alarm a, int level, int count) {
        List<Integer> pool = new ArrayList<>();
        if (a.useMath) pool.add(MATH);
        if (a.useShake) pool.add(SHAKE);
        if (a.useMemory) pool.add(MEMORY);
        if (a.useType) pool.add(TYPE);
        if (a.useShout) pool.add(SHOUT);
        if (pool.isEmpty()) pool.add(MATH);

        int n = Math.max(1, Math.min(5, count));
        Random r = new Random();
        int avoid = AlarmStore.prefs(c).getInt("last_challenge", 0);
        List<Challenge> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int type = pick(pool, avoid, r);
            avoid = type;
            AlarmStore.prefs(c).edit().putInt("last_challenge", type).apply();
            out.add(make(c, type, Math.min(5, level + (i / 2))));
        }
        return out;
    }

    public static Challenge make(Context c, int type, int level) {
        switch (type) {
            case SHAKE:
                return new ShakeChallenge(c, level);
            case MEMORY:
                return new MemoryChallenge(c, level);
            case TYPE:
                return new TypeChallenge(c, level);
            case SHOUT:
                return new ShoutChallenge(c, level);
            case MATH:
            default:
                return new MathChallenge(c, level);
        }
    }

    private static int pick(List<Integer> pool, int avoid, Random r) {
        if (pool.size() == 1) return pool.get(0);
        for (int i = 0; i < 12; i++) {
            int v = pool.get(r.nextInt(pool.size()));
            if (v != avoid) return v;
        }
        for (int v : pool) if (v != avoid) return v;
        return pool.get(0);
    }
}
