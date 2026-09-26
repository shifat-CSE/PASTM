package service;

import javafx.application.Platform;
import model.Session;

import java.util.concurrent.*;

public class ThreadManager {

    private static ThreadManager instance;

    private final ExecutorService calcPool = Executors.newFixedThreadPool(2);
    private final ExecutorService consumer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "session-consumer");
        t.setDaemon(true);
        return t;
    });

    private final SessionQueue queue = new SessionQueue();
    private final SessionCounter counter = new SessionCounter();
    private volatile boolean shuttingDown = false;

    private ThreadManager() {}

    public static synchronized ThreadManager getInstance() {
        if (instance == null) instance = new ThreadManager();
        return instance;
    }

    public SessionQueue getQueue() { return queue; }
    public SessionCounter getCounter() { return counter; }

    public void startConsumer(Runnable onConsumedUiUpdate) {
        consumer.submit(() -> {
            System.out.println("[consumer] started");
            while (!shuttingDown) {
                try {
                    Session s = queue.take();
                    if (s == null) break;
                    counter.add(s);
                    System.out.println("[consumer] processed: " + s.getActivity()
                            + " (" + s.getDurationFormatted() + ")");
                    if (onConsumedUiUpdate != null) {
                        Platform.runLater(onConsumedUiUpdate);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[consumer] exiting");
        });
    }

    public <T> Future<T> submitCalculation(Callable<T> task) {
        return calcPool.submit(task);
    }

    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        System.out.println("[ThreadManager] shutting down");

        queue.signalShutdown();
        consumer.shutdownNow();
        calcPool.shutdown();

        try {
            if (!calcPool.awaitTermination(3, TimeUnit.SECONDS)) {
                calcPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            calcPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[ThreadManager] shutdown complete");
    }
}