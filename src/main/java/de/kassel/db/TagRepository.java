package de.kassel.db;

import de.kassel.model.Tag;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TagRepository {

    // ---- Mapping ----
    private Tag mapRow(ResultSet rs) throws SQLException {
        return new Tag(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("color"),
                rs.getLong("created_at")
        );
    }

    private List<Tag> mapAll(ResultSet rs) throws SQLException {
        List<Tag> out = new ArrayList<>();
        while (rs.next()) out.add(mapRow(rs));
        return out;
    }

    // ---- CRUD ----
    public List<Tag> findAll() {
        String sql = "SELECT id, name, color, created_at FROM tag ORDER BY name ASC";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            return mapAll(rs);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<Tag> findById(long id) {
        String sql = "SELECT id, name, color, created_at FROM tag WHERE id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public long insert(Tag t) {
        String sql = """
            INSERT INTO tag(name, color, created_at)
            VALUES (?, ?, strftime('%s','now'))
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.name());
            if (t.color() == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, t.color());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("no key");
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int update(Tag t) {
        String sql = "UPDATE tag SET name=?, color=? WHERE id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setString(1, t.name());
            if (t.color() == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, t.color());
            ps.setLong(3, t.id());
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int deleteById(long id) {
        String sql = "DELETE FROM tag WHERE id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // ---- Idea<->Tag (Kreuztabelle) ----

    /** Alle Tags zu einer Idea. */
    public List<Tag> findTagsForIdea(long ideaId) {
        String sql = """
            SELECT t.id, t.name, t.color, t.created_at
            FROM tag t
            JOIN idea_tag it ON it.tag_id = t.id
            WHERE it.idea_id = ?
            ORDER BY t.name ASC
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            try (var rs = ps.executeQuery()) { return mapAll(rs); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /** Tag an Idea hängen (idempotent – ignoriert Duplikate). */
    public int addTagToIdea(long ideaId, long tagId) {
        String sql = """
            INSERT OR IGNORE INTO idea_tag(idea_id, tag_id)
            VALUES (?, ?)
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            ps.setLong(2, tagId);
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /** Tag von Idea lösen. */
    public int removeTagFromIdea(long ideaId, long tagId) {
        String sql = "DELETE FROM idea_tag WHERE idea_id=? AND tag_id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            ps.setLong(2, tagId);
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /** Liefert existierenden Tag oder legt ihn an (case-insensitiv einzigartig). */
    public Tag ensureExists(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Tag-Name leer");

        try (var c = DbManager.getConnection()) {
            // existiert?
            try (var ps = c.prepareStatement("SELECT id, name FROM tag WHERE LOWER(name)=LOWER(?)")) {
                ps.setString(1, name);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Tag(rs.getLong("id"), rs.getString("name"), null, 0L);
                    }
                }
            }
            // neu anlegen
            try (var ins = c.prepareStatement(
                    "INSERT INTO tag(name, created_at) VALUES(?, strftime('%s','now'))",
                    java.sql.Statement.RETURN_GENERATED_KEYS
            )) {
                ins.setString(1, name);
                ins.executeUpdate();
                try (var keys = ins.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new Tag(keys.getLong(1), name, null, 0L);
                    }
                }
            }
            // Fallback: erneut lesen
            try (var ps = c.prepareStatement("SELECT id, name FROM tag WHERE LOWER(name)=LOWER(?)")) {
                ps.setString(1, name);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Tag(rs.getLong("id"), rs.getString("name"), null, 0L);
                    }
                }
            }
            throw new RuntimeException("ensureExists failed for: " + name);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /** Ersetzt alle Tags einer Idee durch die (ggf. neuen) Namen. */
    public void replaceIdeaTags(long ideaId, java.util.List<String> tagNames) {
        if (tagNames == null) tagNames = java.util.List.of();

        try (var c = DbManager.getConnection()) {
            c.setAutoCommit(false);
            try (var del = c.prepareStatement("DELETE FROM idea_tag WHERE idea_id=?")) {
                del.setLong(1, ideaId);
                del.executeUpdate();
            }

            try (var ins = c.prepareStatement(
                    "INSERT OR IGNORE INTO idea_tag(idea_id, tag_id) VALUES(?, ?)"
            )) {
                for (String raw : tagNames) {
                    String name = raw == null ? "" : raw.trim();
                    if (name.isEmpty()) continue;

                    var tag = ensureExists(name);  // legt das Tag an/holt es
                    ins.setLong(1, ideaId);
                    ins.setLong(2, tag.id());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            c.commit();
        } catch (Exception e) {
            throw new RuntimeException("replaceIdeaTags failed", e);
        }
    }

}
