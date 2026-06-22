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
}
