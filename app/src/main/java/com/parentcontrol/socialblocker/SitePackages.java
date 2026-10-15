package com.parentcontrol.socialblocker;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Android apps of each site the blocker knows, for the daily budget: the minutes are counted
 * in these apps. A site whose apps are not installed contributes nothing, and so does a browser.
 */
public final class SitePackages {

    private static final Map<String, List<String>> BY_SITE = new LinkedHashMap<>();

    static {
        BY_SITE.put("YouTube", Arrays.asList(
                "com.google.android.youtube", "com.google.android.apps.youtube.music",
                "com.google.android.youtube.tv", "com.google.android.apps.youtube.kids",
                "com.google.android.apps.youtube.creator",
                "app.revanced.android.youtube", "app.rvx.android.youtube",
                "org.schabi.newpipe", "com.github.libretube", "net.newpipe.newplayer"));
        BY_SITE.put("Instagram", Arrays.asList(
                "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona"));
        BY_SITE.put("TikTok", Arrays.asList(
                "com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go",
                "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite"));
        BY_SITE.put("Reddit", Arrays.asList(
                "com.reddit.frontpage",
                "ml.docilealligator.infinityforreddit", "me.ccrama.redditslide",
                "com.laurencedawson.reddit_sync", "com.laurencedawson.reddit_sync.pro",
                "com.andrewshu.android.reddit", "com.andrewshu.android.reddit.pro",
                "com.rubenmayayo.reddit"));
        BY_SITE.put("X", Arrays.asList(
                "com.twitter.android", "com.twitter.android.lite", "org.mariotaku.twidere"));
        BY_SITE.put("Substack", Collections.singletonList("com.substack.app"));
    }

    private SitePackages() {}

    /** The known packages of one site; empty for a site the table does not know. */
    public static List<String> of(String site) {
        List<String> l = BY_SITE.get(site);
        return l == null ? Collections.<String>emptyList() : l;
    }

    /** The known packages of all the given sites. */
    public static Set<String> packages(Collection<String> sites) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : sites) out.addAll(of(s));
        return out;
    }
}
