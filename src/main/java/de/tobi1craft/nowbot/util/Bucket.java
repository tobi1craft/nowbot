package de.tobi1craft.nowbot.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe sliding-window bucket.
 * Allows at most {@code capacity} hits during {@code windowMs}.
 */
public class Bucket {

    private final int capacity;
    private final long windowMs;
    private final Deque<Long> hits = new ArrayDeque<>();

    public Bucket(int capacity, long windowMs) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (windowMs <= 0) throw new IllegalArgumentException("windowMs must be > 0");
        this.capacity = capacity;
        this.windowMs = windowMs;
    }

    /**
     * Consumes one slot if available.
     *
     * @return true if the slot was consumed, false otherwise.
     */
    public synchronized boolean tryConsume() {
        long now = System.currentTimeMillis();
        evictExpired(now);
        if (hits.size() >= capacity) return false;
        hits.addLast(now);
        return true;
    }

    /**
     * @return milliseconds until next slot is available (0 if available now).
     */
    public synchronized long getWaitMs() {
        long now = System.currentTimeMillis();
        evictExpired(now);
        if (hits.size() < capacity) return 0L;
        Long oldest = hits.peekFirst();
        if (oldest == null) return 0L;
        return Math.max(0L, windowMs - (now - oldest));
    }

    private void evictExpired(long now) {
        while (!hits.isEmpty() && now - hits.peekFirst() >= windowMs) {
            hits.removeFirst();
        }
    }
}
