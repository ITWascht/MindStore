package de.kassel.db;

import java.sql.*;
import java.util.Optional;

public class SettingsRepository {

    public SettingsRepository() {
        ensureTable();
    }

    /** Erstellt die Tabelle settings, falls sie nicht existiert. */
    private void ensureTable() {
        try (var c = DbManager.getConnection();
             var st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Erstellen der settings-Tabelle", e);
        }
    }

    /** Liest einen Wert aus den Settings. */
    public String get(String key, String defaultValue) {
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
                return defaultValue;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    /** Liest optionalen Wert (falls du mal prüfen willst, ob er existiert). */
    public Optional<String> getOptional(String key) {
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("value")) : Optional.empty();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /** Schreibt oder aktualisiert einen Eintrag. */
    public void set(String key, String value) {
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO settings (key, value)
                 VALUES (?, ?)
                 ON CONFLICT(key) DO UPDATE SET value = excluded.value
             """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Löscht einen Eintrag. */
    public void delete(String key) {
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement("DELETE FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
