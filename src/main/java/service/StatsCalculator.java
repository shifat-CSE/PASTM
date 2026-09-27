package service;

import model.Session;

import java.util.List;
import java.util.concurrent.Callable;

public class StatsCalculator implements Callable<String> {

    private final List<Session> snapshot;

    public StatsCalculator(List<Session> snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public String call() {
        int totalSec = 0, count = 0, rated = 0, ratingSum = 0;
        int screen = 0, study = 0, sleep = 0;

        for (Session s : snapshot) {
            count++;
            totalSec += s.getDurationSeconds();
            if (s.getRating() > 0) { rated++; ratingSum += s.getRating(); }
            switch (s.getCategory()) {
                case "Screen Time": screen += s.getDurationSeconds(); break;
                case "Study Time":  study  += s.getDurationSeconds(); break;
                case "Sleep Time":  sleep  += s.getDurationSeconds(); break;
            }
        }

        double avg = rated > 0 ? (double) ratingSum / rated : 0;
        return String.format(
                "Sessions: %d%n" +
                        "Total time: %s%n" +
                        "Average rating: %.1f / 5%n" +
                        "─── By category ───%n" +
                        "Screen Time: %s%n" +
                        "Study Time:  %s%n" +
                        "Sleep Time:  %s",
                count, fmt(totalSec), avg, fmt(screen), fmt(study), fmt(sleep));
    }

    private String fmt(int s) {
        int h = s / 3600;
        int m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}