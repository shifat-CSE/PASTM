package model;

import java.time.LocalDate;

public class JournalEntry {

    private LocalDate date;
    private String morning;
    private String evening;

    public JournalEntry(LocalDate date, String morning, String evening) {
        this.date = date;
        this.morning = morning == null ? "" : morning;
        this.evening = evening == null ? "" : evening;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getMorning() { return morning; }
    public void setMorning(String morning) { this.morning = morning; }

    public String getEvening() { return evening; }
    public void setEvening(String evening) { this.evening = evening; }

    public int getTotalWords() { return countWords(morning) + countWords(evening); }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    @Override
    public String toString() {
        return "Journal " + date + " (" + getTotalWords() + " words)";
    }
}