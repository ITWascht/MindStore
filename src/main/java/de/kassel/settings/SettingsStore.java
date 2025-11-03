package de.kassel.settings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SettingsStore {
    private static final String APP_DIR = System.getProperty("user.home") + "/.mindstore";
    private static final File FILE = new File(APP_DIR, "settings.properties");

    public static AppSettings load() {
        try {
            if (!FILE.exists()) return AppSettings.defaults();
            var p = new Properties();
            try (var in = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
                p.load(in);
            }
            int refresh = Integer.parseInt(p.getProperty("reminderRefreshSeconds", "30"));
            int snooze  = Integer.parseInt(p.getProperty("defaultSnoozeMinutes", "10"));
            String start = p.getProperty("startupView", "Inbox");
            return new AppSettings(refresh, snooze, start);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AppSettings.defaults();
        }
    }

    public static void save(AppSettings s) {
        try {
            new File(APP_DIR).mkdirs();
            var p = new Properties();
            p.setProperty("reminderRefreshSeconds", Integer.toString(s.reminderRefreshSeconds()));
            p.setProperty("defaultSnoozeMinutes", Integer.toString(s.defaultSnoozeMinutes()));
            p.setProperty("startupView", s.startupView());
            try (var out = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
                p.store(out, "MindStore Settings");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
