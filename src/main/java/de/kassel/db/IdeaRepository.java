package de.kassel.db;

import de.kassel.model.Idea;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class IdeaRepository {

    public List<Idea> findAll() {
        String sql = "SELECT id, title, body, priority, status FROM idea ORDER BY created_at DESC";
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
        String sql = "SELECT id, title, body, priority, status FROM idea WHERE status=? ORDER BY created_at DESC";
        try (Connection c = DbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Idea> mapAll(ResultSet rs) throws SQLException {
        List<Idea> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new Idea(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getInt("priority"),
                    rs.getString("status")
            ));
        }
        return list;
    }
}
