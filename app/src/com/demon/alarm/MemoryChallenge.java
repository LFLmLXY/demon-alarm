package com.demon.alarm;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** Show a number for a couple of seconds, then you have to type it back from memory. */
public class MemoryChallenge extends Challenge {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String code;
    private final int revealMs;
    private TextView prompt;
    private TextView bigCode;
    private EditText input;
    private Button ok;
    private TextView feedback;
    private TextView countdown;
    private boolean revealed = true;
    private int secondsLeft;

    public MemoryChallenge(Context c, int level) {
        super(c, level);
        int len = Math.min(9, 4 + level);
        StringBuilder sb = new StringBuilder();
        Random r = new Random();
        for (int i = 0; i < len; i++) sb.append(r.nextInt(10));
        this.code = sb.toString();
        this.revealMs = 1200 + len * 450;
    }

    @Override
    public String title() {
        return "记住 " + code.length() + " 位数字";
    }

    @Override
    public View createView() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(UI.dp(ctx, 4), UI.dp(ctx, 12), UI.dp(ctx, 4), UI.dp(ctx, 12));

        prompt = UI.text(ctx, "记住这串数字", 16, UI.DIM);
        prompt.setGravity(Gravity.CENTER);
        root.addView(prompt);

        bigCode = UI.text(ctx, code, 46, UI.YELLOW);
        bigCode.setLetterSpacing(0.25f);
        bigCode.setGravity(Gravity.CENTER);
        bigCode.setPadding(0, UI.dp(ctx, 14), 0, UI.dp(ctx, 6));
        root.addView(bigCode);

        countdown = UI.text(ctx, "", 14, UI.DIM);
        countdown.setGravity(Gravity.CENTER);
        root.addView(countdown);

        input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(26);
        input.setTextColor(UI.TEXT);
        input.setHintTextColor(UI.DIM);
        input.setHint("输入刚才的数字");
        input.setGravity(Gravity.CENTER);
        input.setBackground(UI.roundStroke(UI.CARD2, UI.STROKE, ctx, 14, 1));
        input.setPadding(UI.dp(ctx, 14), UI.dp(ctx, 12), UI.dp(ctx, 14), UI.dp(ctx, 12));
        input.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(ctx, 10);
        root.addView(input, lp);

        feedback = UI.text(ctx, "", 15, UI.RED);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(0, UI.dp(ctx, 8), 0, 0);
        root.addView(feedback);

        ok = mkButton("提交", UI.RED, 0xFFFFFFFF);
        ok.setVisibility(View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = UI.dp(ctx, 12);
        root.addView(ok, bl);
        ok.setOnClickListener(v -> submit());

        startReveal();
        return root;
    }

    private void startReveal() {
        revealed = true;
        secondsLeft = (int) Math.ceil(revealMs / 1000.0);
        tickDown();
    }

    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            if (!revealed) return;
            secondsLeft--;
            if (secondsLeft > 0) {
                if (countdown != null) countdown.setText(secondsLeft + " 秒后消失");
                handler.postDelayed(this, 1000);
            } else {
                hideCode();
            }
        }
    };

    private void tickDown() {
        if (countdown != null) countdown.setText(secondsLeft + " 秒后消失");
        handler.postDelayed(tickTask, 1000);
    }

    private void hideCode() {
        revealed = false;
        handler.removeCallbacks(tickTask);
        if (bigCode != null) {
            bigCode.setText("•  •  •  •");
            bigCode.setTextColor(UI.CARD);
        }
        if (countdown != null) countdown.setText("数字已消失");
        if (prompt != null) prompt.setText("凭记忆输入刚才的数字");
        if (input != null) input.setVisibility(View.VISIBLE);
        if (ok != null) ok.setVisibility(View.VISIBLE);
    }

    private void submit() {
        if (revealed) return;
        String raw = input.getText().toString().trim();
        if (raw.equals(code)) {
            feedback.setTextColor(UI.GREEN);
            feedback.setText("✔ 记忆力不错");
            solved();
        } else {
            input.setText("");
            feedback.setText("✘ 不对，再想想");
        }
    }

    @Override
    public void release() {
        handler.removeCallbacksAndMessages(null);
    }
}
