package database;

import model.UserProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserProfileDAO {

    /** Loads the single profile, creating a default one if none exists. */
    public UserProfile load() throws SQLException {
        String sql = "SELECT name, email, avatar, motto, theme FROM user_profile WHERE id = 1";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new UserProfile(
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("avatar"),
                        rs.getString("motto"),
                        rs.getString("theme"));
            }
        }
        // No row yet — create default and return it
        UserProfile def = new UserProfile();
        save(def);
        return def;
    }

    /** Upserts the single profile row. */
    public void save(UserProfile p) throws SQLException {
        String sql = "INSERT OR REPLACE INTO user_profile(id, name, email, avatar, motto, theme) " +
                "VALUES(1, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, p.getEmail());
            ps.setString(3, p.getAvatar());
            ps.setString(4, p.getMotto());
            ps.setString(5, p.getTheme());
            ps.executeUpdate();
        }
    }

    /** Convenience: save just the theme, preserving other fields. */
    public void saveTheme(String theme) throws SQLException {
        UserProfile p = load();
        p.setTheme(theme);
        save(p);
    }
}