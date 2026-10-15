package com.parentcontrol.socialblocker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The daily budget: how long the apps of the chosen sites were in front today, and whether a
 * lookup is blocked right now. Plain Java, nothing from Android, so the whole chain is tested
 * from synthetic event lists (see BudgetTest).
 */
public final class Budget {

    public static final int RESUMED = 1, PAUSED = 2, STOPPED = 3, SCREEN_OFF = 4, BOOT = 5;

    /** One usage event, in our own terms. [pkg] and [cls] are empty for the screen and device events. */
    public static final class Event {
        public final long t;
        public final int kind;
        public final String pkg;
        public final String cls;

        public Event(long t, int kind, String pkg, String cls) {
            this.t = t;
            this.kind = kind;
            this.pkg = pkg == null ? "" : pkg;
            this.cls = cls == null ? "" : cls;
        }
    }

    private Budget() {}

    /**
     * Milliseconds one of the [counted] packages was in front between [from] and [to], from the
     * events of a range that starts before [from]. Each activity's RESUMED opens a stretch that its
     * PAUSED or STOPPED closes; another package's RESUMED closes it too (one app in front at a
     * time: a PAUSED the system lost would otherwise run for days), the screen going off or the
     * device stopping or starting closes everything, and so does [to] for whatever is still open.
     * Stretches of two activities of one package may overlap: the union is counted, once.
     */
    public static long spent(List<Event> events, Set<String> counted, long from, long to) {
        List<Event> sorted = new ArrayList<>(events);
        Collections.sort(sorted, new Comparator<Event>() {
            @Override public int compare(Event a, Event b) { return Long.compare(a.t, b.t); }
        });
        Map<String, Long> open = new LinkedHashMap<>();   // pkg|cls -> start, counted packages only
        List<long[]> raw = new ArrayList<>();
        for (Event e : sorted) {
            switch (e.kind) {
                case RESUMED:
                    if (e.pkg.isEmpty()) break;
                    // whatever else was in front is not any more
                    List<String> gone = new ArrayList<>();
                    for (Map.Entry<String, Long> o : open.entrySet()) {
                        if (!o.getKey().startsWith(e.pkg + "|")) { raw.add(new long[]{o.getValue(), e.t}); gone.add(o.getKey()); }
                    }
                    for (String k : gone) open.remove(k);
                    if (counted.contains(e.pkg)) {
                        String key = e.pkg + "|" + e.cls;
                        if (!open.containsKey(key)) open.put(key, e.t);
                    }
                    break;
                case PAUSED:
                case STOPPED:
                    Long start = open.remove(e.pkg + "|" + e.cls);
                    if (start != null) raw.add(new long[]{start, e.t});
                    break;
                case SCREEN_OFF:
                case BOOT:
                    for (Long s : open.values()) raw.add(new long[]{s, e.t});
                    open.clear();
                    break;
                default:
                    break;
            }
        }
        for (Long s : open.values()) raw.add(new long[]{s, to});
        return union(raw, from, to);
    }

    /** The length of the union of the stretches, clipped to [from, to]. */
    static long union(List<long[]> stretches, long from, long to) {
        List<long[]> clipped = new ArrayList<>();
        for (long[] s : stretches) {
            long a = Math.max(s[0], from), b = Math.min(s[1], to);
            if (b > a) clipped.add(new long[]{a, b});
        }
        Collections.sort(clipped, new Comparator<long[]>() {
            @Override public int compare(long[] a, long[] b) { return Long.compare(a[0], b[0]); }
        });
        long total = 0, curStart = Long.MIN_VALUE, curEnd = Long.MIN_VALUE;
        for (long[] s : clipped) {
            if (s[0] > curEnd) { if (curEnd > curStart) total += curEnd - curStart; curStart = s[0]; curEnd = s[1]; }
            else if (s[1] > curEnd) curEnd = s[1];
        }
        if (curEnd > curStart) total += curEnd - curStart;
        return total;
    }

    /** True when a budget is set and today's minutes are spent. */
    public static boolean exhausted(int budgetMinutes, long spentMs) {
        return budgetMinutes > 0 && spentMs >= budgetMinutes * 60_000L;
    }

    /** The whole minutes left today, 0 once spent. */
    public static int minutesLeft(int budgetMinutes, long spentMs) {
        if (budgetMinutes <= 0) return 0;
        long left = budgetMinutes * 60_000L - spentMs;
        return left <= 0 ? 0 : (int) ((left + 59_999L) / 60_000L);
    }

    /**
     * Whether a lookup for a chosen site is answered with nothing at [hour] today. Without a
     * budget, the allowed hours decide as always (none = blocked all day). With one, the sites
     * are blocked once it is spent, and outside the allowed hours when there are any: when both
     * would block, blocking wins; with a budget and no hours, the budget alone decides.
     */
    public static boolean shouldBlock(String schedule, int hour, int budgetMinutes, boolean exhaustedToday) {
        if (budgetMinutes <= 0) return ScheduleChecker.shouldBlock(schedule, hour);
        if (exhaustedToday) return true;
        return schedule != null && !schedule.trim().isEmpty() && ScheduleChecker.shouldBlock(schedule, hour);
    }

    /** The local calendar day as one number, 20261015 for the 15th of October 2026. */
    public static int dayKey(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    /** The first millisecond of the day of [c]. */
    public static long dayStart(Calendar c) {
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0); d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    /** The first millisecond of the day after [c]. */
    public static long nextMidnight(Calendar c) {
        Calendar d = (Calendar) c.clone();
        d.setTimeInMillis(dayStart(c));
        d.add(Calendar.DAY_OF_MONTH, 1);
        return d.getTimeInMillis();
    }

    /** All the known packages of the given sites. */
    public static Set<String> packagesOf(Collection<String> sites) {
        return SitePackages.packages(sites);
    }
}
