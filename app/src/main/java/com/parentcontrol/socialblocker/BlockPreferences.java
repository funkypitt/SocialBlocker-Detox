package com.parentcontrol.socialblocker;

import android.content.Context;
import android.content.SharedPreferences;

public class BlockPreferences {

    private static final String PREFS_NAME = "social_blocker_prefs";
    private static final String KEY_BLOCKING_ENABLED = "blocking_enabled";
    private static final String KEY_SCHEDULE = "schedule";
    private static final String KEY_SHOWN_ALWAYS_ON = "shown_always_on_hint";
    private static final String KEY_BLOCK_YOUTUBE = "block_youtube";
    private static final String KEY_BLOCK_INSTAGRAM = "block_instagram";
    private static final String KEY_BLOCK_TIKTOK = "block_tiktok";
    private static final String KEY_BLOCK_REDDIT = "block_reddit";
    private static final String KEY_BLOCK_X = "block_x";
    private static final String KEY_BLOCK_SUBSTACK = "block_substack";
    private static final String KEY_REQUIRE_MATH = "require_math_to_unblock";
    private static final String KEY_BUDGET = "budget_minutes";
    private static final String KEY_BUDGET_DAY = "budget_day";
    private static final String KEY_BUDGET_SPENT = "budget_spent_ms";

    private final SharedPreferences prefs;

    public BlockPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isBlockingEnabled() {
        return prefs.getBoolean(KEY_BLOCKING_ENABLED, false);
    }

    public void setBlockingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, enabled).apply();
    }

    public String getSchedule() {
        return prefs.getString(KEY_SCHEDULE, "");
    }

    public void setSchedule(String schedule) {
        prefs.edit().putString(KEY_SCHEDULE, schedule).apply();
    }

    public boolean hasShownAlwaysOnHint() {
        return prefs.getBoolean(KEY_SHOWN_ALWAYS_ON, false);
    }

    public void setShownAlwaysOnHint(boolean shown) {
        prefs.edit().putBoolean(KEY_SHOWN_ALWAYS_ON, shown).apply();
    }

    // Per-platform toggles (all enabled by default)

    public boolean isYoutubeBlocked() {
        return prefs.getBoolean(KEY_BLOCK_YOUTUBE, true);
    }

    public void setYoutubeBlocked(boolean blocked) {
        prefs.edit().putBoolean(KEY_BLOCK_YOUTUBE, blocked).apply();
    }

    public boolean isInstagramBlocked() {
        return prefs.getBoolean(KEY_BLOCK_INSTAGRAM, true);
    }

    public void setInstagramBlocked(boolean blocked) {
        prefs.edit().putBoolean(KEY_BLOCK_INSTAGRAM, blocked).apply();
    }

    public boolean isTiktokBlocked() {
        return prefs.getBoolean(KEY_BLOCK_TIKTOK, true);
    }

    public void setTiktokBlocked(boolean blocked) {
        prefs.edit().putBoolean(KEY_BLOCK_TIKTOK, blocked).apply();
    }

    public boolean isRedditBlocked() {
        return prefs.getBoolean(KEY_BLOCK_REDDIT, true);
    }

    public void setRedditBlocked(boolean blocked) {
        prefs.edit().putBoolean(KEY_BLOCK_REDDIT, blocked).apply();
    }

    public boolean isXBlocked() {
        return prefs.getBoolean(KEY_BLOCK_X, true);
    }

    public void setXBlocked(boolean blocked) {
        prefs.edit().putBoolean(KEY_BLOCK_X, blocked).apply();
    }

    public boolean isSubstackBlocked() {
        return prefs.getBoolean(KEY_BLOCK_SUBSTACK, true);
    }

    public void setSubstackBlocked(boolean blocked) {
        prefs.edit().putBoolean(KEY_BLOCK_SUBSTACK, blocked).apply();
    }

    // Require solving math problems before blocking can be turned off (default off)

    public boolean isRequireMathToUnblock() {
        return prefs.getBoolean(KEY_REQUIRE_MATH, false);
    }

    public void setRequireMathToUnblock(boolean required) {
        prefs.edit().putBoolean(KEY_REQUIRE_MATH, required).apply();
    }

    // The daily budget: minutes a day in the apps of the chosen sites, 0 = none (default)

    public int getBudgetMinutes() {
        return prefs.getInt(KEY_BUDGET, 0);
    }

    public void setBudgetMinutes(int minutes) {
        prefs.edit().putInt(KEY_BUDGET, Math.max(0, minutes)).apply();
    }

    /** What the last measure found: [spentMs] in front on the day [dayKey] (see Budget.dayKey). */
    public void setBudgetSpent(int dayKey, long spentMs) {
        prefs.edit().putInt(KEY_BUDGET_DAY, dayKey).putLong(KEY_BUDGET_SPENT, spentMs).apply();
    }

    /** The last measure of the day [dayKey]; 0 when the last measure was another day's. */
    public long getBudgetSpentMs(int dayKey) {
        return prefs.getInt(KEY_BUDGET_DAY, 0) == dayKey ? prefs.getLong(KEY_BUDGET_SPENT, 0L) : 0L;
    }
}
