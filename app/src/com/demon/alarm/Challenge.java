package com.demon.alarm;

import android.content.Context;
import android.view.View;
import android.widget.Button;

/** One anti-sleep task. The alarm can only be stopped after every challenge reports solved(). */
public abstract class Challenge {
    protected final Context ctx;
    protected final int level;
    private Runnable onSolved;

    public Challenge(Context ctx, int level) {
        this.ctx = ctx;
        this.level = level;
    }

    public final void setOnSolved(Runnable r) {
        this.onSolved = r;
    }

    protected final void solved() {
        if (onSolved != null) onSolved.run();
    }

    /** Shown in the "第 1 / 3 关" header. */
    public abstract String title();

    /** Build the fresh view for this task. */
    public abstract View createView();

    /** Called when the task is done / replaced, so sensors and recorders can be released. */
    public void release() {
    }

    protected Button mkButton(String label, int bgColor, int textColor) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(17);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setBackground(UI.round(bgColor, ctx, 14));
        b.setPadding(UI.dp(ctx, 16), UI.dp(ctx, 12), UI.dp(ctx, 16), UI.dp(ctx, 12));
        return b;
    }
}
