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

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(final String client) {
        Instant now = clockService.now();
        Instant cutoff = now.minus(WINDOW);
        if (attempts.size() > PRUNE_ABOVE) {
            attempts.values().removeIf(window -> isStale(window, cutoff));
        }
        Deque<Instant> window = attempts.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= LIMIT) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    private boolean isStale(final Deque<Instant> window, final Instant cutoff) {
        synchronized (window) {
            return window.isEmpty() || window.peekLast().isBefore(cutoff);
        }
    }
}
