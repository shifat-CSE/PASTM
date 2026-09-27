package service;

import javafx.application.Platform;

public class TimerThread extends Thread {

    public interface TickListener {
        void onTick(int seconds);
        void onFinish(int seconds);
    }

    private volatile boolean running = true;
    private int seconds = 0;
    private final TickListener listener;

    public TimerThread(String name, int startSeconds, TickListener listener) {
        super(name);
        this.listener = listener;
        this.seconds = startSeconds;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            while (running) {
                Thread.sleep(1000);
                if (!running) break;
                seconds++;
                final int s = seconds;
                Platform.runLater(() -> listener.onTick(s));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        final int finalSeconds = seconds;
        Platform.runLater(() -> listener.onFinish(finalSeconds));
    }

    public void stopTimer() {
        running = false;
        this.interrupt();
    }

    public int getElapsedSeconds() { return seconds; }
}