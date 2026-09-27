package database;

import model.Mood;
import model.Session;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SessionDAO {

    public void insert(Session s) throws SQLException {
        String sql = "INSERT INTO sessions(activity, category, duration_seconds, rating, mood, notes, favorite, date, start_hour) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getActivity());
            ps.setString(2, s.getCategory());
            ps.setInt(3, s.getDurationSeconds());
            ps.setInt(4, s.getRating());
            ps.setString(5, s.getMood().name());
            ps.setString(6, s.getNotes());
            ps.setInt(7, s.isFavorite() ? 1 : 0);
            ps.setString(8, s.getDate().toString());
            ps.setInt(9, s.getStartHour());
            ps.executeUpdate();
        }
    }

    public void updateFavorite(Session s) throws SQLException {
        String sql = "UPDATE sessions SET favorite = ? " +
                "WHERE activity = ? AND category = ? AND date = ? AND start_hour = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, s.isFavorite() ? 1 : 0);
            ps.setString(2, s.getActivity());
            ps.setString(3, s.getCategory());
            ps.setString(4, s.getDate().toString());
            ps.setInt(5, s.getStartHour());
            ps.executeUpdate();
        }
    }

    public void updateNotesAndRating(Session s) throws SQLException {
        String sql = "UPDATE sessions SET notes = ?, rating = ? " +
                "WHERE activity = ? AND category = ? AND date = ? AND start_hour = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getNotes());
            ps.setInt(2, s.getRating());
            ps.setString(3, s.getActivity());
            ps.setString(4, s.getCategory());
            ps.setString(5, s.getDate().toString());
            ps.setInt(6, s.getStartHour());
            ps.executeUpdate();
        }
    }

    public void delete(Session s) throws SQLException {
        String sql = "DELETE FROM sessions " +
                "WHERE activity = ? AND category = ? AND date = ? AND start_hour = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getActivity());
            ps.setString(2, s.getCategory());
            ps.setString(3, s.getDate().toString());
            ps.setInt(4, s.getStartHour());
            ps.executeUpdate();
        }
    }

    public void deleteAll() throws SQLException {
        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM sessions");
        }
    }

    public List<Session> getAll() throws SQLException {
        return query("SELECT activity, category, duration_seconds, rating, mood, notes, favorite, date, start_hour " +
                "FROM sessions ORDER BY id DESC");
    }

    public List<Session> getLast7Days() throws SQLException {
        String from = LocalDate.now().minusDays(6).toString();
        String sql = "SELECT activity, category, duration_seconds, rating, mood, notes, favorite, date, start_hour " +
                "FROM sessions WHERE date >= ? ORDER BY date DESC, id DESC";
        List<Session> list = new ArrayList<>();
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(readSession(rs));
            }
        }
        return list;
    }

    public List<String> getAllActiveDates() throws SQLException {
        List<String> dates = new ArrayList<>();
        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DISTINCT date FROM sessions ORDER BY date DESC")) {
            while (rs.next()) dates.add(rs.getString("date"));
        }
        return dates;
    }

    private List<Session> query(String sql) throws SQLException {
        List<Session> list = new ArrayList<>();
        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(readSession(rs));
        }
        return list;
    }

    private Session readSession(ResultSet rs) throws SQLException {
        Session s = new Session(
                rs.getString("activity"),
                rs.getString("category"),
                rs.getInt("duration_seconds"),
                rs.getInt("rating"),
                Mood.valueOf(rs.getString("mood")),
                rs.getString("notes"),
                rs.getInt("favorite") == 1,
                LocalDate.parse(rs.getString("date"))
        );
        s.setStartHour(rs.getInt("start_hour"));
        return s;
    }
}