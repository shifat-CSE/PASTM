package model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Session {

    private String activity;
    private String category;
    private int durationSeconds;
    private int rating;
    private Mood mood;
    private String notes;
    private boolean favorite;
    private LocalDate date;
    private int startHour;

    public Session(String activity, String category, Mood mood) {
        this.activity = activity;
        this.category = category;
        this.mood = mood;
        this.date = LocalDate.now();
        this.startHour = LocalTime.now().getHour();
        this.durationSeconds = 0;
        this.rating = 0;
        this.notes = "";
        this.favorite = false;
    }

    public Session(String activity, String category, int durationSeconds,
                   int rating, Mood mood, String notes, boolean favorite, LocalDate date) {
        this.activity = activity;
        this.category = category;
        this.durationSeconds = durationSeconds;
        this.rating = rating;
        this.mood = mood;
        this.notes = notes;
        this.favorite = favorite;
        this.date = date;
        this.startHour = 12;
    }

    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int s) { this.durationSeconds = s; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public Mood getMood() { return mood; }
    public void setMood(Mood mood) { this.mood = mood; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes == null ? "" : notes; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public String getDurationFormatted() {
        if (durationSeconds < 60) return durationSeconds + "s";
        int m = durationSeconds / 60;
        int s = durationSeconds % 60;
        if (m >= 60) return (m / 60) + "h " + (m % 60) + "m";
        return m + "m " + String.format("%02d", s) + "s";
    }

    public String getRatingDisplay() {
        if (rating <= 0) return "not rated";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) sb.append(i <= rating ? "▰" : "▱");
        sb.append(" ").append(rating).append("/5");
        return sb.toString();
    }

    @Override
    public String toString() {
        return activity + " (" + category + ", " + getDurationFormatted() + ")";
    }
}