package de.kassel.db;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.stream.Collectors;

public class DbManager {
    private static final String APP_DIR = System.getProperty("user.home") + "/.mindstore";
    private static final String DB_URL  = "jdbc:sqlite:" + APP_DIR + "/mindstore.db";
    private static volatile boolean initialized = false;

    private DbManager() {}

    public static Connection getConnection() throws SQLException {
        new java.io.File(APP_DIR).mkdirs();

        Connection c = DriverManager.getConnection(DB_URL);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
        }
        if (!initialized) {
            synchronized (DbManager.class) {
                if (!initialized) {
                    initSchema(c);
                    initialized = true;
                }
            }
        }
        return c;
    }

    private static void initSchema(Connection c) {
        try (var in = DbManager.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql not found at /db/schema.sql");

            String sql = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            var statements = splitSqlStatements(sql);

            try (Statement st = c.createStatement()) {
                // 1) PRAGMAs aus Datei (falls vorhanden) VOR der Transaktion ausführen
                for (String s : statements) {
                    if (s.trim().toUpperCase().startsWith("PRAGMA ")) {
                        st.execute(s);
                    }
                }

                // 2) Alle übrigen Statements in EINER Transaktion ausführen
                st.execute("BEGIN;");
                for (String s : statements) {
                    String up = s.trim().toUpperCase();
                    if (up.startsWith("PRAGMA ")
                            || up.startsWith("BEGIN")
                            || up.startsWith("START TRANSACTION")
                            || up.startsWith("COMMIT")
                            || up.startsWith("END TRANSACTION")) {
                        continue; // Marker/PRAGMAs auslassen
                    }
                    try {
                        st.execute(s);
                    } catch (Exception ex) {
                        System.err.println("SQL failed: " + s);
                        throw ex;
                    }
                }
                st.execute("COMMIT;");
            }

            // 3) Seed-Daten (falls leer)
            seedIfEmpty(c);

            // 4) Migrationen / nachträgliche Spalten
//            ensureReminderNoteColumn(c);  // <- fügt reminder.note hinzu, falls fehlt
            ensureDeletedAtColumn(c);     // <- fügt idea.deleted_at hinzu, falls fehlt
            ensureAttachmentTable(c);
            ensureAttachmentFolder();

        } catch (Exception e) {
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    public static void seedIfEmpty(Connection c) throws SQLException {
        try (var ps = c.prepareStatement("SELECT COUNT(*) FROM idea");
             var rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                try (var ins = c.prepareStatement(
                        "INSERT INTO idea(title, body, priority, status, created_at) " +
                                "VALUES (?,?,?,?, strftime('%s','now'))")) {
                    ins.setString(1, "Erste echte DB-Idee");
                    ins.setString(2, "Hallo aus SQLite");
                    ins.setInt(3, 1);
                    ins.setString(4, "inbox");
                    ins.executeUpdate();

                    ins.setString(1, "FXML laden");
                    ins.setString(2, "View-Switching testen");
                    ins.setInt(3, 2);
                    ins.setString(4, "doing");
                    ins.executeUpdate();

                    ins.setString(1, "Archiv-Demo");
                    ins.setString(2, "Zeigt Filter archived");
                    ins.setInt(3, 3);
                    ins.setString(4, "archived");
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            // ignoriere: Tabelle evtl. noch nicht erstellt, initSchema kümmert sich
        }
    }

    /**
     * Zerlegt ein SQL-Skript in Statements.
     * - Normale Statements enden am Semikolon
     * - CREATE TRIGGER … END; wird als EIN Block behandelt
     */
    private static java.util.List<String> splitSqlStatements(String script) {
        java.util.List<String> stmts = new java.util.ArrayList<>();
        String[] lines = script.split("\\r?\\n");
        StringBuilder buf = new StringBuilder();
        boolean inTrigger = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!inTrigger) {
                if (trimmed.regionMatches(true, 0, "CREATE TRIGGER", 0, "CREATE TRIGGER".length())) {
                    inTrigger = true;
                    buf.setLength(0);
                    buf.append(line).append("\n");
                    continue;
                }
                buf.append(line).append("\n");
                String current = buf.toString();
                int idx;
                while ((idx = current.indexOf(';')) >= 0) {
                    String stmt = current.substring(0, idx).trim();
                    if (!stmt.isEmpty()) stmts.add(stmt + ";");
                    current = current.substring(idx + 1);
                }
                buf.setLength(0);
                buf.append(current);
            } else {
                buf.append(line).append("\n");
                // Trigger endet mit "END;" (Case-insensitive)
                if (trimmed.equalsIgnoreCase("END;") || trimmed.equalsIgnoreCase("END")) {
                    String stmt = buf.toString().trim();
                    if (!stmt.endsWith(";")) stmt = stmt + ";";
                    stmts.add(stmt);
                    buf.setLength(0);
                    inTrigger = false;
                }
            }
        }
        // Rest anhängen
        String rest = buf.toString().trim();
        if (!rest.isEmpty()) {
            if (!rest.endsWith(";")) rest = rest + ";";
            stmts.add(rest);
        }
        return stmts;
    }

    /** Fügt idea.deleted_at (INTEGER) hinzu, falls die Spalte fehlt. */
    private static void ensureDeletedAtColumn(Connection c) {
        try (var st = c.createStatement()) {
            boolean has = false;
            try (var rs = st.executeQuery("PRAGMA table_info(idea)")) {
                while (rs.next()) {
                    if ("deleted_at".equalsIgnoreCase(rs.getString("name"))) {
                        has = true; break;
                    }
                }
            }
            if (!has) {
                st.execute("ALTER TABLE idea ADD COLUMN deleted_at INTEGER");
                st.execute("CREATE INDEX IF NOT EXISTS idx_idea_deleted_at ON idea(deleted_at)");
                System.out.println("⚙️  Spalte 'deleted_at' in idea nachgerüstet.");
            }
        } catch (Exception ignore) {
            // bei sehr alten SQLite-Versionen ggf. kein ALTER – dann Migration später lösen
        }
    }

    private static void ensureAttachmentTable(Connection c) {
        try (var st = c.createStatement()) {
            // Prüfen, ob Tabelle existiert
            boolean exists = false;
            try (var rs = st.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='attachment'")) {
                exists = rs.next();
            }

            if (!exists) {
                // Tabelle + Index anlegen
                st.execute("""
                CREATE TABLE IF NOT EXISTS attachment (
                  id         INTEGER PRIMARY KEY AUTOINCREMENT,
                  idea_id    INTEGER NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
                  file_name  TEXT NOT NULL,
                  file_path  TEXT NOT NULL,
                  size_bytes INTEGER,
                  mime_type  TEXT,
                  created_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
                );
            """);

                st.execute("""
                CREATE INDEX IF NOT EXISTS idx_attachment_idea_id ON attachment(idea_id);
            """);
            }
        } catch (Exception e) {
            // nicht hart failen – nur loggen
            System.err.println("ensureAttachmentTable failed: " + e.getMessage());
        }
    }

    private static void ensureAttachmentFolder() {
        try {
            var dir = java.nio.file.Paths.get(System.getProperty("user.home"), ".mindstore", "attachments");
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception ignore) { }
    }


}
