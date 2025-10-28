package de.kassel.db;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.stream.Collectors;

public class DbManager {
    private static final String APP_DIR = System.getProperty("user.home") + "/.mindstore";
    private static  final String DB_URL = "jdbc:sqlite:" + APP_DIR + "/mindstore.db";
    private static volatile boolean initialized = false;

    private DbManager() {}

    public static Connection getConnection() throws SQLException {
        new java.io.File(APP_DIR).mkdirs();

        Connection c = DriverManager.getConnection(DB_URL);
        try (Statement st = c.createStatement()){
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=Normal;");
        }
        if (!initialized){
            synchronized (DbManager.class){
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
            if (in == null) {
                throw new IllegalStateException("schema.sql nicht gefunden: /db/schema.sql");
            }
            var sql = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            try (Statement st = c.createStatement()) {
                // 1) PRAGMAs VOR der Transaktion ausführen
                for (String raw : sql.split(";\\s*(\\r?\\n|$)")) {
                    String s = raw.trim();
                    if (s.isEmpty()) continue;
                    if (s.regionMatches(true, 0, "PRAGMA ", 0, 7)) {
                        st.execute(s + ";");
                    }
                }

                // 2) Restliche Statements IN einer Transaktion ausführen
                st.execute("BEGIN;");
                for (String raw : sql.split(";\\s*(\\r?\\n|$)")) {
                    String s = raw.trim();
                    if (s.isEmpty()) continue;
                    if (s.regionMatches(true, 0, "PRAGMA ", 0, 7)) continue; // PRAGMAs hier überspringen
                    try {
                        st.execute(s + ";");
                    } catch (Exception ex) {
                        System.err.println("SQL failed: " + s); // -> zeigt dir die kaputte Zeile
                        throw ex;
                    }
                }
                st.execute("COMMIT;");
            }

            seedIfEmpty(c);
        } catch (Exception e) {
            throw new RuntimeException("Schema-Initialisierung fehlgeschlagen", e);
        }
    }

    public static void seedIfEmpty(Connection c) throws SQLException {
        try (var ps = c.prepareStatement("SELECT COUNT(*) FROM idea");
             var rs = ps.executeQuery()){
            if (rs.next() && rs.getInt(1) == 0) {
                try (var ins = c.prepareStatement(
                        "INSERT INTO idea(title, body, priority, status, created_at) VALUES (?,?,?,?, strftime('%s','now'))")) {
                    ins.setString(1, "Erste echte DB-Idee");
                    ins.setString(2, "Hallo aus SQLite 👋");
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
            // erstmal leer lassen, Tabelle wird noch erstellt
        }
    }
}
