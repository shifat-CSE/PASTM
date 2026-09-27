package model;

import java.time.LocalDate;

public class JournalEntry {

    private LocalDate date;
    private String morning;
    private String noon;
    private String afternoon;
    private String evening;
    private String night;

    public JournalEntry(LocalDate date) {
        this(date, "", "", "", "", "");
    }

    public JournalEntry(LocalDate date, String morning, String noon,
                        String afternoon, String evening, String night) {
        this.date = date;
        this.morning   = morning   == null ? "" : morning;
        this.noon      = noon      == null ? "" : noon;
        this.afternoon = afternoon == null ? "" : afternoon;
        this.evening   = evening   == null ? "" : evening;
        this.night     = night     == null ? "" : night;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getMorning() { return morning; }
    public void setMorning(String morning) { this.morning = morning; }

    public String getNoon() { return noon; }
    public void setNoon(String noon) { this.noon = noon; }

    public String getAfternoon() { return afternoon; }
    public void setAfternoon(String afternoon) { this.afternoon = afternoon; }

    public String getEvening() { return evening; }
    public void setEvening(String evening) { this.evening = evening; }

    public String getNight() { return night; }
    public void setNight(String night) { this.night = night; }

    public int getTotalWords() {
        return countWords(morning) + countWords(noon) + countWords(afternoon)
                + countWords(evening) + countWords(night);
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    @Override
    public String toString() {
        return "Journal " + date + " (" + getTotalWords() + " words)";
    }
}