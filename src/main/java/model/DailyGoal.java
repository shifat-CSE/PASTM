package model;

import java.time.LocalDate;

public class DailyGoal {

    private LocalDate date;
    private String category;      // "Study Time" | "Screen Time" | "Sleep Time"
    private int targetMinutes;

    public DailyGoal(LocalDate date, String category, int targetMinutes) {
        this.date = date;
        this.category = category;
        this.targetMinutes = targetMinutes;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getTargetMinutes() { return targetMinutes; }
    public void setTargetMinutes(int targetMinutes) { this.targetMinutes = targetMinutes; }

    @Override
    public String toString() {
        return date + " " + category + " → " + targetMinutes + "m";
    }
}