package com.parentcontrol.socialblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BudgetTest {

    private static final long MIN = 60_000L;
    private static final long DAY = 1_000_000_000L;   // "midnight" of the synthetic day
    private static final Set<String> COUNTED = new HashSet<>(Arrays.asList("com.instagram.android", "com.google.android.youtube"));

    private static Budget.Event ev(long t, int kind, String pkg) { return new Budget.Event(t, kind, pkg, pkg.isEmpty() ? "" : pkg + ".Main"); }
    private static Budget.Event ev(long t, int kind, String pkg, String cls) { return new Budget.Event(t, kind, pkg, cls); }

    private static long spent(long to, Budget.Event... events) {
        return Budget.spent(new ArrayList<>(Arrays.asList(events)), COUNTED, DAY, to);
    }

    @Test
    public void oneStretchCountsItsLength() {
        assertEquals(5 * MIN, spent(DAY + 60 * MIN,
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.instagram.android"),
                ev(DAY + 15 * MIN, Budget.PAUSED, "com.instagram.android")));
    }

    @Test
    public void otherPackagesCountNothing() {
        assertEquals(0, spent(DAY + 60 * MIN,
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.whatsapp"),
                ev(DAY + 15 * MIN, Budget.PAUSED, "com.whatsapp")));
    }

    @Test
    public void anotherAppInFrontClosesTheStretch() {
        // the PAUSED of instagram was lost: whatsapp coming in front ends it
        assertEquals(3 * MIN, spent(DAY + 60 * MIN,
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.instagram.android"),
                ev(DAY + 13 * MIN, Budget.RESUMED, "com.whatsapp"),
                ev(DAY + 40 * MIN, Budget.PAUSED, "com.whatsapp")));
    }

    @Test
    public void screenOffClosesTheStretch() {
        assertEquals(2 * MIN, spent(DAY + 60 * MIN,
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.instagram.android"),
                ev(DAY + 12 * MIN, Budget.SCREEN_OFF, "")));
    }

    @Test
    public void stillOpenRunsToNow() {
        assertEquals(4 * MIN, spent(DAY + 14 * MIN,
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.instagram.android")));
    }

    @Test
    public void stretchFromYesterdayIsClippedAtMidnight() {
        assertEquals(7 * MIN, spent(DAY + 60 * MIN,
                ev(DAY - 20 * MIN, Budget.RESUMED, "com.google.android.youtube"),
                ev(DAY + 7 * MIN, Budget.PAUSED, "com.google.android.youtube")));
    }

    @Test
    public void twoActivitiesOfOneAppOverlapOnce() {
        // the player activity opens over the feed: 10 → 20 in all, not 10 + 8
        assertEquals(10 * MIN, spent(DAY + 60 * MIN,
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.google.android.youtube", "Feed"),
                ev(DAY + 12 * MIN, Budget.RESUMED, "com.google.android.youtube", "Player"),
                ev(DAY + 12 * MIN + 100, Budget.PAUSED, "com.google.android.youtube", "Feed"),
                ev(DAY + 20 * MIN, Budget.PAUSED, "com.google.android.youtube", "Player")));
    }

    @Test
    public void twoAppsAddUp() {
        assertEquals(9 * MIN, spent(DAY + 60 * MIN,
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.instagram.android"),
                ev(DAY + 14 * MIN, Budget.PAUSED, "com.instagram.android"),
                ev(DAY + 30 * MIN, Budget.RESUMED, "com.google.android.youtube"),
                ev(DAY + 35 * MIN, Budget.PAUSED, "com.google.android.youtube")));
    }

    @Test
    public void unsortedEventsAreSorted() {
        assertEquals(5 * MIN, spent(DAY + 60 * MIN,
                ev(DAY + 15 * MIN, Budget.PAUSED, "com.instagram.android"),
                ev(DAY + 10 * MIN, Budget.RESUMED, "com.instagram.android")));
    }

    @Test
    public void exhaustedAndMinutesLeft() {
        assertFalse(Budget.exhausted(0, 999 * MIN));
        assertFalse(Budget.exhausted(30, 29 * MIN + 59_000));
        assertTrue(Budget.exhausted(30, 30 * MIN));
        assertEquals(0, Budget.minutesLeft(0, 0));
        assertEquals(30, Budget.minutesLeft(30, 0));
        assertEquals(1, Budget.minutesLeft(30, 29 * MIN + 1));
        assertEquals(1, Budget.minutesLeft(30, 29 * MIN));
        assertEquals(0, Budget.minutesLeft(30, 30 * MIN));
        assertEquals(0, Budget.minutesLeft(30, 45 * MIN));
    }

    @Test
    public void withoutBudgetTheHoursDecideAsBefore() {
        assertTrue(Budget.shouldBlock("", 12, 0, false));          // no hours = all day
        assertFalse(Budget.shouldBlock("6-8,18-22", 7, 0, false)); // inside
        assertTrue(Budget.shouldBlock("6-8,18-22", 12, 0, false)); // outside
    }

    @Test
    public void budgetAloneOpensUntilSpent() {
        assertFalse(Budget.shouldBlock("", 12, 30, false));
        assertTrue(Budget.shouldBlock("", 12, 30, true));
        assertTrue(Budget.shouldBlock(null, 12, 30, true));
    }

    @Test
    public void budgetAndHoursBlockWhenEitherWould() {
        assertFalse(Budget.shouldBlock("6-8,18-22", 7, 30, false)); // inside, minutes left
        assertTrue(Budget.shouldBlock("6-8,18-22", 7, 30, true));   // inside, spent
        assertTrue(Budget.shouldBlock("6-8,18-22", 12, 30, false)); // outside, minutes left
        assertTrue(Budget.shouldBlock("6-8,18-22", 12, 30, true));  // outside, spent
    }

    @Test
    public void dayKeyAndBounds() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.OCTOBER, 15, 14, 30, 0);
        c.set(Calendar.MILLISECOND, 0);
        assertEquals(20261015, Budget.dayKey(c));
        Calendar s = Calendar.getInstance();
        s.setTimeInMillis(Budget.dayStart(c));
        assertEquals(0, s.get(Calendar.HOUR_OF_DAY));
        assertEquals(15, s.get(Calendar.DAY_OF_MONTH));
        Calendar m = Calendar.getInstance();
        m.setTimeInMillis(Budget.nextMidnight(c));
        assertEquals(0, m.get(Calendar.HOUR_OF_DAY));
        assertEquals(16, m.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void sitePackages() {
        Set<String> p = SitePackages.packages(Arrays.asList("YouTube", "X", "Nowhere"));
        assertTrue(p.contains("com.google.android.youtube"));
        assertTrue(p.contains("com.twitter.android"));
        assertFalse(p.contains("com.instagram.android"));
        assertTrue(SitePackages.of("Nowhere").isEmpty());
    }

    /** Android 11+ hides the other apps unless the manifest names them: every known package must be declared. */
    @Test
    public void everyKnownPackageIsVisibleToTheApp() throws Exception {
        java.io.File manifest = new java.io.File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) manifest = new java.io.File("app/src/main/AndroidManifest.xml");
        String xml = new String(java.nio.file.Files.readAllBytes(manifest.toPath()), "UTF-8");
        for (String site : Arrays.asList("YouTube", "Instagram", "TikTok", "Reddit", "X", "Substack")) {
            assertFalse(site, SitePackages.of(site).isEmpty());
            for (String p : SitePackages.of(site)) assertTrue(p, xml.contains("<package android:name=\"" + p + "\" />"));
        }
    }
}
