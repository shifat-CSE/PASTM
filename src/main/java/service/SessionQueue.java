package service;

import model.Session;

import java.util.LinkedList;
import java.util.Queue;

public class SessionQueue {

    private final Queue<Session> queue = new LinkedList<>();
    private static final int CAPACITY = 100;
    private volatile boolean shuttingDown = false;

    public synchronized void put(Session s) throws InterruptedException {
        while (queue.size() >= CAPACITY && !shuttingDown) {
            wait();
        }
        if (shuttingDown) return;
        queue.add(s);
        notifyAll();
    }

    public synchronized Session take() throws InterruptedException {
        while (queue.isEmpty() && !shuttingDown) {
            wait();
        }
        if (queue.isEmpty()) return null;
        Session s = queue.poll();
        notifyAll();
        return s;
    }

    public synchronized void signalShutdown() {
        shuttingDown = true;
        notifyAll();
    }

    public synchronized int size() { return queue.size(); }

    public synchronized void clear() {
        queue.clear();
        notifyAll();
    }
}