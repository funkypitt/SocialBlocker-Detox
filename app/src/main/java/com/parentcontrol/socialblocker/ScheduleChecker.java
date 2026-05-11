package com.parentcontrol.socialblocker;

import java.util.Calendar;

public class ScheduleChecker {

    /**
     * Check if the current time falls within any of the allowed time ranges.
     * Schedule format: "6-8,18-22" means allowed from 6:00-8:00 and 18:00-22:00.
     *
     * @param schedule The schedule string, e.g., "6-8,18-22"
     * @return true if blocking should be active (current time is NOT in an allowed range)
     */
    public static boolean shouldBlock(String schedule) {
        if (schedule == null || schedule.trim().isEmpty()) {
            return true; // No schedule = always block
        }

        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String[] ranges = schedule.split(",");

        for (String range : ranges) {
            range = range.trim();
            if (range.isEmpty()) continue;

            String[] parts = range.split("-");
            if (parts.length != 2) continue;

            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());

                if (start < 0 || start > 23 || end < 0 || end > 23) continue;

                if (start <= end) {
                    // Normal range, e.g., 6-8
                    if (currentHour >= start && currentHour < end) {
                        return false; // In allowed range, don't block
                    }
                } else {
                    // Overnight range, e.g., 22-6
                    if (currentHour >= start || currentHour < end) {
                        return false; // In allowed range, don't block
                    }
                }
            } catch (NumberFormatException e) {
                // Skip malformed range
            }
        }

        return true; // Not in any allowed range, block
    }
}
