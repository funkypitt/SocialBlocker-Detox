package com.parentcontrol.socialblocker;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Random;

/**
 * Math challenge gate. Ported from the Social Blocker browser extension (blocked.js):
 * the user must solve 5 increasingly difficult math problems to unblock.
 * Returns RESULT_OK only when all 5 are solved correctly.
 */
public class MathChallengeActivity extends AppCompatActivity {

    private static final int TOTAL_LEVELS = 5;

    private static final String[] DIFFICULTY_LABELS = {
            "Difficulty: Easy",
            "Difficulty: Medium",
            "Difficulty: Hard",
            "Difficulty: Very Hard",
            "Difficulty: Expert"
    };

    private static final String[] DIFFICULTY_COLORS = {
            "#6bff6b",
            "#ffd93d",
            "#ffa500",
            "#ff6b6b",
            "#ff00ff"
    };

    private final Random rng = new Random();

    private int currentLevel = 0;
    private int wrongAttempts = 0;
    private Problem currentProblem;

    private TextView questionEl;
    private TextInputEditText answerInput;
    private TextView feedbackEl;
    private TextView hintEl;
    private TextView qNumEl;
    private TextView diffLabel;
    private View progressFill;
    private View progressTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_math_challenge);

        questionEl = findViewById(R.id.question);
        answerInput = findViewById(R.id.answer);
        feedbackEl = findViewById(R.id.feedback);
        hintEl = findViewById(R.id.hint);
        qNumEl = findViewById(R.id.qNum);
        diffLabel = findViewById(R.id.diffLabel);
        progressFill = findViewById(R.id.progressFill);
        progressTrack = findViewById(R.id.progressTrack);

        MaterialButton submitBtn = findViewById(R.id.submitAnswer);
        submitBtn.setOnClickListener(v -> checkAnswer());
        answerInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkAnswer();
                return true;
            }
            return false;
        });

        loadQuestion();
    }

    private void loadQuestion() {
        wrongAttempts = 0;
        currentProblem = generate(currentLevel);
        questionEl.setText(currentProblem.question);
        answerInput.setText("");
        feedbackEl.setText("");
        hintEl.setText("");
        qNumEl.setText(getString(R.string.challenge_question_counter, currentLevel + 1, TOTAL_LEVELS));
        diffLabel.setText(DIFFICULTY_LABELS[currentLevel]);
        diffLabel.setTextColor(android.graphics.Color.parseColor(DIFFICULTY_COLORS[currentLevel]));
        updateProgress();
        answerInput.requestFocus();
    }

    private void updateProgress() {
        progressTrack.post(() -> {
            int trackWidth = progressTrack.getWidth();
            android.view.ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
            lp.width = (int) (trackWidth * (currentLevel / (float) TOTAL_LEVELS));
            progressFill.setLayoutParams(lp);
        });
    }

    private void checkAnswer() {
        String userAnswer = answerInput.getText() == null ? "" : answerInput.getText().toString().trim();
        if (userAnswer.isEmpty()) return;

        double numAnswer;
        try {
            numAnswer = Double.parseDouble(userAnswer);
        } catch (NumberFormatException e) {
            feedbackEl.setText("Please enter a number.");
            feedbackEl.setTextColor(ContextCompat.getColor(this, R.color.red_primary));
            return;
        }

        // Allow small floating point tolerance
        if (Math.abs(numAnswer - currentProblem.answer) < 0.01) {
            feedbackEl.setText("Correct!");
            feedbackEl.setTextColor(ContextCompat.getColor(this, R.color.green_active));
            hintEl.setText("");
            currentLevel++;
            updateProgress();

            if (currentLevel >= TOTAL_LEVELS) {
                // All solved — unblock
                feedbackEl.postDelayed(() -> {
                    setResult(RESULT_OK);
                    finish();
                }, 800);
            } else {
                feedbackEl.postDelayed(this::loadQuestion, 800);
            }
        } else {
            wrongAttempts++;
            feedbackEl.setText("Wrong answer. Try again.");
            feedbackEl.setTextColor(ContextCompat.getColor(this, R.color.red_primary));
            answerInput.setText("");
            answerInput.requestFocus();
            if (wrongAttempts >= 2) {
                hintEl.setText("Hint: " + currentProblem.hint);
            }
        }
    }

    // ===================== Problem generators (ported from blocked.js) =====================

    private static class Problem {
        final String question;
        final double answer;
        final String hint;

        Problem(String question, double answer, String hint) {
            this.question = question;
            this.answer = answer;
            this.hint = hint;
        }
    }

    private Problem generate(int level) {
        switch (level) {
            case 0:
                return genLevel1();
            case 1:
                return genLevel2();
            case 2:
                return genLevel3();
            case 3:
                return genLevel4();
            default:
                return genLevel5();
        }
    }

    // Level 1: Basic arithmetic (two operations)
    private Problem genLevel1() {
        int a = randInt(12, 99);
        int b = randInt(12, 99);
        int c = randInt(2, 9);
        long answer = a + (long) b * c;
        return new Problem(
                a + " + " + b + " × " + c + " = ?",
                answer,
                "Remember order of operations: multiply first.");
    }

    // Level 2: Percentage / fraction problems
    private Problem genLevel2() {
        switch (randInt(0, 1)) {
            case 0: {
                int pct = pickFrom(15, 25, 35, 45, 65, 75, 85);
                int base = pickFrom(120, 160, 200, 240, 320, 400, 480, 560);
                return new Problem(
                        "What is " + pct + "% of " + base + "?",
                        pct * base / 100.0,
                        pct + "% = " + pct + "/100");
            }
            default: {
                int a = randInt(11, 49);
                int b = randInt(11, 49);
                int c = randInt(3, 12);
                long answer = (long) a * b - (long) c * c;
                return new Problem(
                        a + " × " + b + " − " + c + "² = ?",
                        answer,
                        "Calculate " + c + "² first, then subtract.");
            }
        }
    }

    // Level 3: Quadratic / exponent problems
    private Problem genLevel3() {
        switch (randInt(0, 1)) {
            case 0: {
                int base = randInt(3, 9);
                int exp = randInt(3, 5);
                int sub = randInt(10, 99);
                long pow = (long) Math.pow(base, exp);
                return new Problem(
                        base + toSuperscript(exp) + " − " + sub + " = ?",
                        pow - sub,
                        base + toSuperscript(exp) + " = " + pow);
            }
            default: {
                // Solve: ax + b = c
                int a = randInt(3, 12);
                int x = randInt(2, 20);
                int b = randInt(5, 50);
                int c = a * x + b;
                return new Problem(
                        "Solve for x: " + a + "x + " + b + " = " + c,
                        x,
                        "Subtract " + b + " from both sides, then divide by " + a + ".");
            }
        }
    }

    // Level 4: Multi-step algebra / roots
    private Problem genLevel4() {
        switch (randInt(0, 2)) {
            case 0: {
                int a = randInt(2, 6);
                int b = randInt(2, 6);
                int product = a * b;
                int sum = a + b;
                return new Problem(
                        "x² − " + sum + "x + " + product + " = 0. Find the larger root.",
                        Math.max(a, b),
                        "Factor: (x − ?)(x − ?) = 0");
            }
            case 1: {
                int n = pickFrom(144, 196, 256, 324, 441, 529, 625, 729, 784, 841, 961);
                int extra = randInt(10, 50);
                return new Problem(
                        "√" + n + " + " + extra + " = ?",
                        Math.sqrt(n) + extra,
                        "What number squared equals " + n + "?");
            }
            default: {
                // System: x + y = S, x - y = D  =>  x = (S+D)/2
                int x = randInt(10, 40);
                int y = randInt(2, x - 1);
                int s = x + y;
                int d = x - y;
                return new Problem(
                        "If x + y = " + s + " and x − y = " + d + ", what is x?",
                        x,
                        "Add both equations together.");
            }
        }
    }

    // Level 5: Harder combinatorics / series / multi-step
    private Problem genLevel5() {
        switch (randInt(0, 3)) {
            case 0: {
                // Sum of arithmetic series
                int a1 = randInt(2, 10);
                int d = randInt(2, 5);
                int n = randInt(8, 15);
                int an = a1 + (n - 1) * d;
                double sum = n * (a1 + an) / 2.0;
                return new Problem(
                        "Sum of arithmetic series: first term = " + a1 + ", common difference = " + d
                                + ", number of terms = " + n + ". What is the sum?",
                        sum,
                        "S = n(a₁ + aₙ)/2, where aₙ = a₁ + (n−1)d");
            }
            case 1: {
                // Modular arithmetic
                int base = randInt(7, 15);
                int exp = randInt(3, 4);
                int mod = pickFrom(7, 11, 13, 17);
                long pow = (long) Math.pow(base, exp);
                return new Problem(
                        "What is " + base + toSuperscript(exp) + " mod " + mod + "?",
                        modPow(base, exp, mod),
                        "Compute " + base + toSuperscript(exp) + " = " + pow
                                + ", then find remainder when divided by " + mod + ".");
            }
            case 2: {
                // Permutations: P(n, r) = n! / (n-r)!
                int n = randInt(5, 8);
                int r = randInt(2, Math.min(4, n));
                long answer = 1;
                for (int i = 0; i < r; i++) answer *= (n - i);
                return new Problem(
                        "How many ways to arrange " + r + " items from " + n
                                + " distinct items? (P(" + n + "," + r + "))",
                        answer,
                        "P(n,r) = n! / (n−r)! = " + n + " × " + (n - 1) + " × ...");
            }
            default: {
                // Quadratic with larger coefficients
                int a = randInt(2, 5);
                int r1 = randInt(1, 8);
                int r2 = randInt(r1 + 1, 12);
                // a(x - r1)(x - r2) = ax^2 - a(r1+r2)x + a*r1*r2
                int b = a * (r1 + r2);
                int c = a * r1 * r2;
                return new Problem(
                        a + "x² − " + b + "x + " + c + " = 0. Find the sum of both roots.",
                        r1 + r2,
                        "Sum of roots = −(−" + b + ")/" + a + " = " + b + "/" + a);
            }
        }
    }

    // ===================== Utility functions =====================

    private int randInt(int min, int max) {
        return rng.nextInt(max - min + 1) + min;
    }

    private int pickFrom(int... arr) {
        return arr[rng.nextInt(arr.length)];
    }

    private static String toSuperscript(int n) {
        char[] map = {'⁰', '¹', '²', '³', '⁴',
                '⁵', '⁶', '⁷', '⁸', '⁹'};
        StringBuilder sb = new StringBuilder();
        for (char ch : String.valueOf(n).toCharArray()) {
            if (ch >= '0' && ch <= '9') {
                sb.append(map[ch - '0']);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static long modPow(int base, int exp, int mod) {
        long result = 1;
        long b = base % mod;
        for (int i = 0; i < exp; i++) {
            result = (result * b) % mod;
        }
        return result;
    }
}
