package database;

import model.JournalEntry;

import java.sql.*;
import java.time.LocalDate;

public class JournalDAO {

    public JournalEntry getByDate(LocalDate date) throws SQLException {
        String sql = "SELECT morning, noon, afternoon, evening, night FROM journal WHERE date = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new JournalEntry(
                            date,
                            rs.getString("morning"),
                            rs.getString("noon"),
                            rs.getString("afternoon"),
                            rs.getString("evening"),
                            rs.getString("night"));
                }
            }
        }
        return new JournalEntry(date);
    }

    public void save(JournalEntry e) throws SQLException {
        String sql = "INSERT OR REPLACE INTO journal(date, morning, noon, afternoon, evening, night) " +
                "VALUES(?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getDate().toString());
            ps.setString(2, e.getMorning());
            ps.setString(3, e.getNoon());
            ps.setString(4, e.getAfternoon());
            ps.setString(5, e.getEvening());
            ps.setString(6, e.getNight());
            ps.executeUpdate();
        }
    }
}