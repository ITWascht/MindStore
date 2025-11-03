package de.kassel.db;

import de.kassel.model.Reminder;

import java.sql.*;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;


public class ReminderRepository {

    public Optional<Reminder> findByIdeaId(long ideaId) {
        String sql = """
            SELECT id, idea_id, due_at, note, is_done, created_at, updated_at
            FROM reminder
            WHERE idea_id = ?
        """;
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Legt eine Erinnerung an oder aktualisiert sie (unique: idea_id). */
    public Long upsertForIdea(long ideaId, long dueAtEpochSec, String note) {
        String sql = """
            INSERT INTO reminder (idea_id, due_at, note, is_done, created_at)
            VALUES (?, ?, ?, 0, strftime('%s','now'))
            ON CONFLICT(idea_id) DO UPDATE SET
                due_at = excluded.due_at,
                note   = excluded.note
        """;
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ideaId);
            ps.setLong(2, dueAtEpochSec);
            ps.setString(3, note);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1); // bei Insert
                }
            }
            // bei Update gibt’s i.d.R. keinen Key → gib ideaId zurück
            return ideaId;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Entfernt die Erinnerung einer Idee (falls Felder geleert wurden). */
    public void deleteForIdea(long ideaId) {
        String sql = "DELETE FROM reminder WHERE idea_id = ?";
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Reminder map(ResultSet rs) throws SQLException {
        long id        = rs.getLong("id");
        long ideaId    = rs.getLong("idea_id");
        long dueAt     = rs.getLong("due_at");
        String note    = rs.getString("note");
        boolean done   = rs.getInt("is_done") == 1;
        long createdAt = rs.getLong("created_at");
        Long updatedAt = rs.getObject("updated_at") == null ? null : rs.getLong("updated_at");
        return new Reminder(id, ideaId, dueAt, note, done, createdAt, updatedAt);
    }

    public List<Reminder> findDueOrUpcoming(long nowEpoch, long horizonSeconds) {
        String sql = """
        SELECT id, idea_id, due_at, note, is_done, created_at, updated_at
        FROM reminder
        WHERE is_done = 0
          AND (
                due_at <= ?                       -- überfällig
             OR (due_at > ? AND due_at <= ?)      -- in den nächsten X Sekunden
          )
        ORDER BY due_at ASC
        """;

        long horizon = nowEpoch + horizonSeconds;

        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {

            ps.setLong(1, nowEpoch);   // überfällig
            ps.setLong(2, nowEpoch);   // jetzt
            ps.setLong(3, horizon);    // bis Horizont

            try (var rs = ps.executeQuery()) {
                var list = new java.util.ArrayList<Reminder>();
                while (rs.next()) {
                    // updated_at kann NULL sein → über getObject abfragen
                    Long updated = rs.getObject("updated_at") == null ? null : rs.getLong("updated_at");

                    list.add(new Reminder(
                            rs.getLong("id"),
                            rs.getLong("idea_id"),
                            rs.getLong("due_at"),
                            rs.getString("note"),
                            rs.getInt("is_done") != 0,   // boolean robust aus INTEGER
                            rs.getLong("created_at"),
                            updated
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void markDone(long reminderId) {
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement("UPDATE reminder SET is_done=1 WHERE id=?")) {
            ps.setLong(1, reminderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void snooze(long reminderId, int minutes) {
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement("UPDATE reminder " +
                     "SET due_at = CAST(strftime('%s','now') AS INTEGER) + ? " +
                     "WHERE id=?")) {

            ps.setLong(1, minutes * 60L);
            ps.setLong(2, reminderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
