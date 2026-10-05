package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most 20 sign-in or registration attempts per client per 15 minutes, shared across both, as
 * amp-auth did. It is in memory, so with more than one replica each keeps its own count and a
 * client could get up to 20 per replica; the chart runs one. A shared store is the fix if that changes.
 */
@Service
@RequiredArgsConstructor
public class AuthRateLimiter {

    static final int LIMIT = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int PRUNE_ABOVE = 10_000;

    private final ClockService clockService;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(final String client) {
        Instant now = clockService.now();
        Instant cutoff = now.minus(WINDOW);
        if (attempts.size() > PRUNE_ABOVE) {
            attempts.values().removeIf(window -> window.isStale(cutoff));
        }
        return attempts.computeIfAbsent(client, key -> new Attempts()).tryRecord(now, cutoff);
    }

    /**
     * One client's recent attempts. Its methods are synchronized on the object itself, so two requests
     * from the same client cannot both see room for the last allowed attempt.
     */
    private static final class Attempts {

        private final Deque<Instant> times = new ArrayDeque<>();

        synchronized boolean tryRecord(final Instant now, final Instant cutoff) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= LIMIT) {
                return false;
            }
            times.addLast(now);
            return true;
        }

        synchronized boolean isStale(final Instant cutoff) {
            return times.isEmpty() || times.peekLast().isBefore(cutoff);
        }
    }
}
