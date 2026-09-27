package database;

import model.DailyGoal;

import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class DailyGoalDAO {

    /** Loads all goals for a given date — category → targetMinutes. */
    public Map<String, Integer> getByDate(LocalDate date) throws SQLException {
        String sql = "SELECT category, target_minutes FROM daily_goals WHERE date = ?";
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString("category"), rs.getInt("target_minutes"));
            }
        }
        return map;
    }

    /** Upsert one goal. */
    public void save(LocalDate date, String category, int targetMinutes) throws SQLException {
        String sql = "INSERT OR REPLACE INTO daily_goals(date, category, target_minutes) VALUES(?, ?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ps.setString(2, category);
            ps.setInt(3, targetMinutes);
            ps.executeUpdate();
        }
    }

    /** Saves all three categories at once. */
    public void saveAll(LocalDate date, int studyMin, int screenMin, int sleepMin) throws SQLException {
        save(date, "Study Time", studyMin);
        save(date, "Screen Time", screenMin);
        save(date, "Sleep Time", sleepMin);
    }
}