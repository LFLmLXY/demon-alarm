package com.demon.alarm;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Type a whole sentence exactly, punctuation included — forces eyes open and real attention. */
public class TypeChallenge extends Challenge {
    private static final String[] PHRASES = {
            "我起来了。",
            "起床，喝水。",
            "我马上起床，不睡了。",
            "起床、洗脸、喝一杯温水。",
            "被窝是青春的坟墓，我真的起来了。",
            "今天也要认真生活，所以现在先站起来。"
    };

    private final String phrase;
    private TextView feedback;

    public TypeChallenge(Context c, int level) {
        super(c, level);
        int idx = Math.min(PHRASES.length - 1, Math.max(0, level - 1));
        this.phrase = PHRASES[idx];
    }

    @Override
    public String title() {
        return "照打这句话";
    }

    @Override
    public View createView() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(UI.dp(ctx, 4), UI.dp(ctx, 12), UI.dp(ctx, 4), UI.dp(ctx, 12));

        TextView tip = UI.text(ctx, "一字不差地打出来（标点也要）", 14, UI.DIM);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        TextView target = UI.text(ctx, phrase, 24, UI.RED_DARK);
        target.setGravity(Gravity.CENTER);
        target.setLineSpacing(UI.dp(ctx, 4), 1f);
        target.setPadding(UI.dp(ctx, 8), UI.dp(ctx, 18), UI.dp(ctx, 8), UI.dp(ctx, 18));
        target.setBackground(UI.roundStroke(UI.CARD2, UI.STROKE, ctx, 16, 1));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = UI.dp(ctx, 10);
        root.addView(target, tl);

        final EditText input = new EditText(ctx);
        input.setTextSize(22);
        input.setTextColor(UI.TEXT);
        input.setHintTextColor(UI.DIM);
        input.setHint("在这里打一遍");
        input.setBackground(UI.roundStroke(UI.CARD2, UI.STROKE, ctx, 14, 1));
        input.setPadding(UI.dp(ctx, 14), UI.dp(ctx, 12), UI.dp(ctx, 14), UI.dp(ctx, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(ctx, 14);
        root.addView(input, lp);

        feedback = UI.text(ctx, "", 15, UI.RED);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(0, UI.dp(ctx, 8), 0, 0);
        root.addView(feedback);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String v = s == null ? "" : s.toString().trim();
                if (v.equals(phrase)) {
                    feedback.setTextColor(UI.GREEN);
                    feedback.setText("✔ 一字不差");
                    solved();
                } else if (v.length() > 0 && !phrase.startsWith(v)) {
                    feedback.setText("✘ 和第上面不一样");
                } else {
                    feedback.setText("");
                }
            }
        });
        return root;
    }
}
