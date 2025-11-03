package de.kassel.settings;

public record AppSettings(
        int reminderRefreshSeconds,   // z.B. 30
        int defaultSnoozeMinutes,     // z.B. 10
        String startupView            // "Inbox", "Alle Ideen", "Board", ...
) {
    public static AppSettings defaults() {
        return new AppSettings(30, 10, "Inbox");
    }
}
