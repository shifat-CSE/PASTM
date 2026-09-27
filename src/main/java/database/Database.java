package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String URL = "jdbc:sqlite:lifesync.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        String sessions = "CREATE TABLE IF NOT EXISTS sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "activity TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "duration_seconds INTEGER NOT NULL DEFAULT 0, " +
                "rating INTEGER NOT NULL DEFAULT 0, " +
                "mood TEXT NOT NULL, " +
                "notes TEXT DEFAULT '', " +
                "favorite INTEGER NOT NULL DEFAULT 0, " +
                "date TEXT NOT NULL, " +
                "start_hour INTEGER NOT NULL DEFAULT 12)";

        String journal = "CREATE TABLE IF NOT EXISTS journal (" +
                "date TEXT PRIMARY KEY, " +
                "morning   TEXT DEFAULT '', " +
                "noon      TEXT DEFAULT '', " +
                "afternoon TEXT DEFAULT '', " +
                "evening   TEXT DEFAULT '', " +
                "night     TEXT DEFAULT '')";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sessions);
            stmt.execute(journal);

            ensureColumn(conn, "sessions", "start_hour", "INTEGER NOT NULL DEFAULT 12");
            ensureColumn(conn, "journal", "noon",      "TEXT DEFAULT ''");
            ensureColumn(conn, "journal", "afternoon", "TEXT DEFAULT ''");
            ensureColumn(conn, "journal", "evening",   "TEXT DEFAULT ''");
            ensureColumn(conn, "journal", "night",     "TEXT DEFAULT ''");

            System.out.println("[Database] initialized: lifesync.db ready.");
        } catch (SQLException e) {
            System.err.println("[Database] initialization failed: " + e.getMessage());
        }
    }

    private static void ensureColumn(Connection conn, String table, String col, String def) throws SQLException {
        boolean found = false;
        try (ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (col.equalsIgnoreCase(rs.getString("name"))) { found = true; break; }
            }
        }
        if (!found) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + def);
                System.out.println("[Migration] Added " + table + "." + col);
            }
        }
    }
}