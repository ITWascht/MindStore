package de.kassel.db;

import de.kassel.model.Attachment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttachmentRepository {

    private Attachment mapRow(ResultSet rs) throws SQLException {
        Long size = rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes");
        return new Attachment(
                rs.getLong("id"),
                rs.getLong("idea_id"),
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getString("mime_type"),
                size,
                rs.getLong("created_at")
        );
    }

    private List<Attachment> mapAll(ResultSet rs) throws SQLException {
        List<Attachment> out = new ArrayList<>();
        while (rs.next()) out.add(mapRow(rs));
        return out;
    }

    public List<Attachment> findByIdea(long ideaId) {
        String sql = """
            SELECT id, idea_id, file_name, file_path, mime_type, size_bytes, created_at
            FROM attachment WHERE idea_id=?
            ORDER BY created_at DESC
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, ideaId);
            try (var rs = ps.executeQuery()) { return mapAll(rs); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<Attachment> findById(long id) {
        String sql = """
            SELECT id, idea_id, file_name, file_path, mime_type, size_bytes, created_at
            FROM attachment WHERE id=?
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public long insert(Attachment a) {
        String sql = """
            INSERT INTO attachment(idea_id, file_name, file_path, mime_type, size_bytes, created_at)
            VALUES (?, ?, ?, ?, ?, strftime('%s','now'))
        """;
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.ideaId());
            ps.setString(2, a.fileName());
            ps.setString(3, a.filePath());
            if (a.mimeType() == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, a.mimeType());
            if (a.sizeBytes() == null) ps.setNull(5, Types.BIGINT); else ps.setLong(5, a.sizeBytes());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("no key");
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int deleteById(long id) {
        String sql = "DELETE FROM attachment WHERE id=?";
        try (var c = DbManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
