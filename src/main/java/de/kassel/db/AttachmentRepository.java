package de.kassel.db;

import de.kassel.model.Attachment;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttachmentRepository {

    private static final Path BASE_DIR = Path.of(
            System.getProperty("user.home"), ".mindstore", "attachments"
    );

    /** Liefert alle Attachments zu einer Idea, chronologisch. */
    public List<Attachment> listForIdea(long ideaId) {
        String sql = """
            SELECT id, idea_id, file_name, file_path, mime_type, size_bytes, created_at
            FROM attachment
            WHERE idea_id = ?
            ORDER BY created_at ASC
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<Attachment>();
                while (rs.next()) {
                    list.add(new Attachment(
                            rs.getLong("id"),
                            rs.getLong("idea_id"),
                            rs.getString("file_name"),
                            rs.getString("file_path"),
                            rs.getString("mime_type"),
                            rs.getObject("size_bytes", Long.class),
                            rs.getLong("created_at")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Alias für ältere Aufrufer. */
    public List<Attachment> findByIdeaId(long ideaId) {
        return listForIdea(ideaId);
    }

    /**
     * Kopiert eine Datei in den App-Speicher (~/.mindstore/attachments/<ideaId>/)
     * und legt einen Datensatz in der DB an.
     * @return die neue Attachment-ID
     */
    public long insertFromPath(long ideaId, Path source) {
        try {
            Files.createDirectories(BASE_DIR.resolve(String.valueOf(ideaId)));
            Path dest = BASE_DIR.resolve(String.valueOf(ideaId))
                    .resolve(source.getFileName().toString());

            // Datei kopieren/überschreiben
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);

            // Metadaten
            String fileName = dest.getFileName().toString();
            String filePath = dest.toString();
            String mime     = null;
            try {
                mime = Files.probeContentType(dest);
            } catch (IOException ignore) { /* optional */ }

            Long sizeBytes = null;
            try {
                sizeBytes = Files.size(dest);
            } catch (IOException ignore) { }

            String sql = """
                INSERT INTO attachment(idea_id, file_name, file_path, mime_type, size_bytes, created_at)
                VALUES(?,?,?,?,?, strftime('%s','now'))
            """;

            try (var c = DbManager.getConnection();
                 var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, ideaId);
                ps.setString(2, fileName);
                ps.setString(3, filePath);
                if (mime != null) ps.setString(4, mime); else ps.setNull(4, Types.VARCHAR);
                if (sizeBytes != null) ps.setLong(5, sizeBytes); else ps.setNull(5, Types.INTEGER);

                ps.executeUpdate();
                try (var keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
                throw new RuntimeException("No generated key for attachment insert");
            }
        } catch (IOException | SQLException ex) {
            throw new RuntimeException("insertFromPath failed: " + source, ex);
        }
    }

    /**
     * Löscht Attachment aus DB (und versucht die Datei zu entfernen).
     */
    public void deleteById(long id) {
        String select = "SELECT file_path FROM attachment WHERE id=?";
        String delete = "DELETE FROM attachment WHERE id=?";
        try (var c = DbManager.getConnection()) {
            String filePath = null;
            try (var ps = c.prepareStatement(select)) {
                ps.setLong(1, id);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        filePath = rs.getString("file_path");
                    }
                }
            }
            try (var ps = c.prepareStatement(delete)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            if (filePath != null) {
                try { Files.deleteIfExists(Path.of(filePath)); } catch (Exception ignore) { }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
