package com.parentcontrol.socialblocker;

import android.content.res.Resources;

import java.util.Random;

/**
 * The math gate's problems. Ported from the Social Blocker browser extension (blocked.js):
 * five increasingly difficult levels, one problem each; stopping the blocker needs all five.
 * Only the wording moved to string resources; the numbers are drawn exactly as before.
 */
public class MathProblems {

    public static final int TOTAL_LEVELS = 5;

    public static class Problem {
        public final String question;
        public final double answer;
        public final String hint;

        Problem(String question, double answer, String hint) {
            this.question = question;
            this.answer = answer;
            this.hint = hint;
        }

        /** Small floating point tolerance, as in the extension. */
        public boolean isRight(double value) {
            return Math.abs(value - answer) < 0.01;
        }
    }

    private final Resources res;
    private final Random rng = new Random();

    public MathProblems(Resources res) {
        this.res = res;
    }

    public Problem generate(int level) {
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
                res.getString(R.string.h_order));
    }

    // Level 2: Percentage / fraction problems
    private Problem genLevel2() {
        switch (randInt(0, 1)) {
            case 0: {
                int pct = pickFrom(15, 25, 35, 45, 65, 75, 85);
                int base = pickFrom(120, 160, 200, 240, 320, 400, 480, 560);
                return new Problem(
                        res.getString(R.string.q_percent, pct, base),
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
                        res.getString(R.string.h_square_first, c + "²"));
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
                        res.getString(R.string.q_solve_x, a + "x + " + b + " = " + c),
                        x,
                        res.getString(R.string.h_subtract_divide, b, a));
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
                        res.getString(R.string.q_larger_root, "x² − " + sum + "x + " + product + " = 0"),
                        Math.max(a, b),
                        res.getString(R.string.h_factor));
            }
            case 1: {
                int n = pickFrom(144, 196, 256, 324, 441, 529, 625, 729, 784, 841, 961);
                int extra = randInt(10, 50);
                return new Problem(
                        "√" + n + " + " + extra + " = ?",
                        Math.sqrt(n) + extra,
                        res.getString(R.string.h_sqrt, n));
            }
            default: {
                // System: x + y = S, x - y = D  =>  x = (S+D)/2
                int x = randInt(10, 40);
                int y = randInt(2, x - 1);
                int s = x + y;
                int d = x - y;
                return new Problem(
                        res.getString(R.string.q_system, s, d),
                        x,
                        res.getString(R.string.h_add_equations));
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
                        res.getString(R.string.q_series, a1, d, n),
                        sum,
                        "S = n(a₁ + aₙ)/2, aₙ = a₁ + (n−1)d");
            }
            case 1: {
                // Modular arithmetic
                int base = randInt(7, 15);
                int exp = randInt(3, 4);
                int mod = pickFrom(7, 11, 13, 17);
                long pow = (long) Math.pow(base, exp);
                return new Problem(
                        res.getString(R.string.q_mod, base + toSuperscript(exp), mod),
                        modPow(base, exp, mod),
                        res.getString(R.string.h_mod, base + toSuperscript(exp) + " = " + pow, mod));
            }
            case 2: {
                // Permutations: P(n, r) = n! / (n-r)!
                int n = randInt(5, 8);
                int r = randInt(2, Math.min(4, n));
                long answer = 1;
                for (int i = 0; i < r; i++) answer *= (n - i);
                return new Problem(
                        res.getString(R.string.q_perm, r, n),
                        answer,
                        "P(n,r) = n! / (n−r)! = " + n + " × " + (n - 1) + " × …");
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
                        res.getString(R.string.q_sum_roots, a + "x² − " + b + "x + " + c + " = 0"),
                        r1 + r2,
                        "x₁ + x₂ = −(−" + b + ")/" + a + " = " + b + "/" + a);
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
