package de.kassel.db;

import de.kassel.model.Idea;
import de.kassel.model.IdeaStatus;
import de.kassel.model.Priority;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import static javax.management.remote.JMXConnectorFactory.connect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class IdeaRepository {

    public List<Idea> findAll() {
        String sql = """
        SELECT id, title, body, priority, status, effort_minutes, created_at, updated_at
        FROM idea
        WHERE deleted_at IS NULL
        ORDER BY created_at DESC
    """;
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Idea> findByStatus(String status) {
        if (status == null || status.equalsIgnoreCase("all")) return findAll();
        String sql = """
        SELECT id, title, body, priority, status, effort_minutes, created_at, updated_at
        FROM idea
        WHERE status = ? AND deleted_at IS NULL
        ORDER BY created_at DESC
    """;
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Idea mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String title = rs.getString("title");
        String body = rs.getString("body");
        Priority priority = Priority.fromInt(rs.getInt("priority"));
        IdeaStatus status = IdeaStatus.fromDb(rs.getString("status"));

        Integer effort = rs.getObject("effort_minutes") == null
                ? null
                : rs.getInt("effort_minutes");

        long createdAt = rs.getLong("created_at"); // in DB: INTEGER (epoch seconds)
        Long updatedAt = rs.getObject("updated_at") == null
                ? null
                : rs.getLong("updated_at");

        return new Idea(id, title, body, priority, status, effort, createdAt, updatedAt);
    }

    private List<Idea> mapAll(ResultSet rs) throws SQLException {
        List<Idea> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    // EINZELN LADEN
    public java.util.Optional<Idea> findById(long id) {
        String sql = """
        SELECT id, title, body, priority, status, effort_minutes, created_at, updated_at
        FROM idea WHERE id = ?
    """;
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.of(mapRow(rs)); // du hast mapRow(...) schon
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // INSERT – gibt die neue ID zurück
    public long insert(de.kassel.model.Idea idea) {
        String sql = "INSERT INTO idea(title, body, priority, status, effort_minutes, created_at) " +
                "VALUES (?, ?, ?, ?, ?, strftime('%s','now'))";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, idea.title());
            ps.setString(2, idea.body());
            ps.setInt(3, idea.priority().level());
            ps.setString(4, idea.status().db());
            if (idea.effortMinutes() == null) ps.setNull(5, java.sql.Types.INTEGER);
            else ps.setInt(5, idea.effortMinutes());

            ps.executeUpdate();

            // 1. Versuch: getGeneratedKeys()
            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }

            // Fallback für SQLite: last_insert_rowid()
            try (var st = c.createStatement();
                 var rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) return rs.getLong(1);
            }

            throw new RuntimeException("Konnte neue Idea-ID nicht ermitteln.");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // INSERT – Variante: vollständiges Idea zurück (inkl. id, created_at)
    public Idea insertReturning(Idea toInsert) {
        long id = insert(toInsert);
        return findById(id).orElseThrow(() -> new RuntimeException("Inserted idea not found: id=" + id));
    }

    // UPDATE – setzt title/body/priority/status/effort; updated_at macht der Trigger
    public int update(Idea idea) {
        String sql = """
        UPDATE idea
        SET title = ?, body = ?, priority = ?, status = ?, effort_minutes = ?
        WHERE id = ?
    """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {

            ps.setString(1, idea.title());
            ps.setString(2, idea.body());
            ps.setInt(3, idea.priority().level());
            ps.setString(4, idea.status().db());
            if (idea.effortMinutes() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, idea.effortMinutes());
            ps.setLong(6, idea.id());

            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // DELETE
    public int deleteById(long id) {
        String sql = "DELETE FROM idea WHERE id = ?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // STATUS wechseln (komfort)
    public int changeStatus(long id, IdeaStatus newStatus) {
        String sql = "UPDATE idea SET status = ? WHERE id = ?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setString(1, newStatus.db());
            ps.setLong(2, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Effort setzen/entfernen
    public int setEffortMinutes(long id, Integer minutes) {
        String sql = "UPDATE idea SET effort_minutes = ? WHERE id = ?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            if (minutes == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, minutes);
            ps.setLong(2, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Einfache Volltext-Suche (Titel+Body)
    public List<Idea> search(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return findAll();
        String sql = """
        SELECT id, title, body, priority, status, effort_minutes, created_at, updated_at
        FROM idea
        WHERE title LIKE ? OR body LIKE ?
        ORDER BY created_at DESC
    """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            String like = "%" + q + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            try (var rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public List<Idea> findByTagId(long tagId) {
        String sql = """
        SELECT i.id, i.title, i.body, i.priority, i.status,
               i.effort_minutes, i.created_at, i.updated_at
        FROM idea i
        JOIN idea_tag it ON it.idea_id = i.id
        WHERE it.tag_id = ?
        ORDER BY i.created_at DESC
    """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, tagId);
            try (var rs = ps.executeQuery()) {
                return mapAll(rs); // nutzt deinen bestehenden Mapper
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void updateStatus(long id, String newStatus) {
        String sql = "UPDATE idea SET status = ?, updated_at = strftime('%s','now') WHERE id = ?";
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed", e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM idea WHERE id = ?";
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete failed", e);
        }
    }

    public List<Idea> findTrash() {
        String sql = """
        SELECT id, title, body, priority, status, effort_minutes, created_at, updated_at, deleted_at
        FROM idea
        WHERE deleted_at IS NOT NULL
        ORDER BY deleted_at DESC
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            return mapAll(rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void softDelete(long id) {
        String sql = "UPDATE idea SET deleted_at = strftime('%s','now') WHERE id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void restore(long id) {
        String sql = "UPDATE idea SET deleted_at = NULL WHERE id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void purge(long id) { // endgültig löschen
        String sql = "DELETE FROM idea WHERE id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void moveToTrash(long id) {
        String sql = "UPDATE idea SET deleted_at = strftime('%s','now') WHERE id = ?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void deletePermanent(long id) {
        String sql = "DELETE FROM idea WHERE id = ?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }





}
