package service;

import javafx.application.Platform;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Week 4: Scheduled daily reminders using ScheduledExecutorService.
 * Fires once per day at each configured time.
 */
public class ReminderService {

    public interface ReminderListener {
        void onReminder(String title, String message);
    }

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3, r -> {
        Thread t = new Thread(r, "reminder-thread");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = true;

    public void start(ReminderListener listener) {
        schedule(9, 0, "Morning Journal", "Time to write your intention for today.", listener);
        schedule(14, 0, "Check-in", "Have you started a study session yet?", listener);
        schedule(22, 30, "Bedtime", "Log your sleep and start winding down.", listener);
        System.out.println("[ReminderService] started (9:00, 14:00, 22:30)");
    }

    private void schedule(int hour, int minute, String title, String message, ReminderListener listener) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!target.isAfter(now)) target = target.plusDays(1);

        long initialDelay = Duration.between(now, target).getSeconds();
        long period = TimeUnit.DAYS.toSeconds(1);

        scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            if (listener != null) {
                Platform.runLater(() -> listener.onReminder(title, message));
            }
        }, initialDelay, period, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        System.out.println("[ReminderService] shutdown");
    }
}