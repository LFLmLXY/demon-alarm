package com.demon.alarm;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** Arithmetic: wrong answer -> the problem is replaced, so you cannot brute force it from bed. */
public class MathChallenge extends Challenge {
    private static final Random RND = new Random();

    private String expr;
    private int answer;
    private TextView prompt;
    private EditText input;
    private TextView feedback;

    public MathChallenge(Context c, int level) {
        super(c, level);
        nextProblem();
    }

    @Override
    public String title() {
        return "算对这道题";
    }

    private void nextProblem() {
        int lv = level;
        int a, b, c, d;
        if (lv <= 1) {
            a = 18 + RND.nextInt(70);
            b = 18 + RND.nextInt(70);
            expr = a + " + " + b;
            answer = a + b;
        } else if (lv == 2) {
            a = 3 + RND.nextInt(6);
            b = 12 + RND.nextInt(18);
            expr = a + " × " + b;
            answer = a * b;
        } else if (lv == 3) {
            a = 12 + RND.nextInt(30);
            b = 12 + RND.nextInt(30);
            c = 2 + RND.nextInt(8);
            expr = "(" + a + " + " + b + ") × " + c;
            answer = (a + b) * c;
        } else if (lv == 4) {
            a = 15 + RND.nextInt(45);
            b = 15 + RND.nextInt(45);
            c = 3 + RND.nextInt(8);
            d = 10 + RND.nextInt(60);
            expr = "(" + a + " + " + b + ") × " + c + " − " + d;
            answer = (a + b) * c - d;
        } else {
            a = 20 + RND.nextInt(60);
            b = 20 + RND.nextInt(60);
            c = 4 + RND.nextInt(9);
            d = (a + b) * c;
            int e = 7 + RND.nextInt(40);
            expr = "(" + a + " + " + b + ") × " + c + " − " + e;
            answer = d - e;
        }
        if (answer <= 0) answer = Math.abs(answer) + 7;
    }

    @Override
    public View createView() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(UI.dp(ctx, 4), UI.dp(ctx, 6), UI.dp(ctx, 4), UI.dp(ctx, 6));

        prompt = UI.text(ctx, expr + " = ?", 30, UI.TEXT);
        prompt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, UI.dp(ctx, 18), 0, UI.dp(ctx, 18));
        root.addView(prompt);

        input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(28);
        input.setTextColor(UI.TEXT);
        input.setHintTextColor(UI.DIM);
        input.setHint("输入答案");
        input.setGravity(Gravity.CENTER);
        input.setBackground(UI.roundStroke(UI.CARD2, UI.STROKE, ctx, 14, 1));
        input.setPadding(UI.dp(ctx, 14), UI.dp(ctx, 12), UI.dp(ctx, 14), UI.dp(ctx, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(input, lp);

        feedback = UI.text(ctx, "", 15, UI.RED);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(0, UI.dp(ctx, 8), 0, 0);
        root.addView(feedback);

        Button ok = mkButton("提交答案", UI.RED, 0xFFFFFFFF);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = UI.dp(ctx, 12);
        root.addView(ok, bl);
        ok.setOnClickListener(v -> submit());
        return root;
    }

    private void submit() {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            feedback.setText("先算出来再按提交");
            return;
        }
        int val;
        try {
            val = Integer.parseInt(raw);
        } catch (Exception e) {
            feedback.setText("只填数字");
            return;
        }
        if (val == answer) {
            feedback.setTextColor(UI.GREEN);
            feedback.setText("✔ 算对了");
            solved();
        } else {
            input.setText("");
            feedback.setText("✘ 错了，换一道");
            nextProblem();
            prompt.setText(expr + " = ?");
        }
    }
}
