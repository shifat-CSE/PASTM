package service;

import model.Session;

public class SessionCounter {

    private int totalSessions;
    private long totalDurationSeconds;
    private int totalRatingPoints;
    private int ratedSessions;

    public synchronized void add(Session s) {
        totalSessions++;
        totalDurationSeconds += s.getDurationSeconds();
        if (s.getRating() > 0) {
            totalRatingPoints += s.getRating();
            ratedSessions++;
        }
    }

    public synchronized void reset() {
        totalSessions = 0;
        totalDurationSeconds = 0;
        totalRatingPoints = 0;
        ratedSessions = 0;
    }

    public synchronized int getTotalSessions() { return totalSessions; }
    public synchronized long getTotalDurationSeconds() { return totalDurationSeconds; }

    public synchronized double getAverageRating() {
        if (ratedSessions == 0) return 0;
        return (double) totalRatingPoints / ratedSessions;
    }

    public synchronized String getSummary() {
        return String.format("%d sessions | %s | %.1f avg",
                totalSessions, formatDuration((int) totalDurationSeconds), getAverageRating());
    }

    private String formatDuration(int s) {
        int h = s / 3600;
        int m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}