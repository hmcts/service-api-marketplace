package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    @Mock
    private ClockService clockService;

    private AuthRateLimiter limiter;

    @BeforeEach
    void start() {
        lenient().when(clockService.now()).thenReturn(NOW);
        limiter = new AuthRateLimiter(clockService);
    }

    private void use(final String client, final int times) {
        for (int i = 0; i < times; i++) {
            assertThat(limiter.tryAcquire(client)).isTrue();
        }
    }

    @Test
    void twenty_attempts_should_be_allowed_and_the_twenty_first_refused() {
        use("203.0.113.9", AuthRateLimiter.LIMIT);

        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
    }

    @Test
    void each_client_should_have_its_own_allowance() {
        use("203.0.113.9", AuthRateLimiter.LIMIT);

        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
        assertThat(limiter.tryAcquire("198.51.100.4")).isTrue();
    }

    @Test
    void attempts_should_stop_counting_once_they_are_older_than_fifteen_minutes() {
        use("203.0.113.9", AuthRateLimiter.LIMIT);

        lenient().when(clockService.now()).thenReturn(NOW.plus(AuthRateLimiter.WINDOW).minusSeconds(1));
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();

        lenient().when(clockService.now()).thenReturn(NOW.plus(AuthRateLimiter.WINDOW).plusSeconds(1));
        assertThat(limiter.tryAcquire("203.0.113.9")).isTrue();
    }

    @Test
    void the_allowance_should_slide_rather_than_reset_all_at_once() {
        use("203.0.113.9", 10);
        lenient().when(clockService.now()).thenReturn(NOW.plus(Duration.ofMinutes(10)));
        use("203.0.113.9", 10);
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();

        // The first ten age out; the later ten do not.
        lenient().when(clockService.now()).thenReturn(NOW.plus(Duration.ofMinutes(16)));
        use("203.0.113.9", 10);
        assertThat(limiter.tryAcquire("203.0.113.9")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void clients_that_have_gone_quiet_should_be_forgotten_so_memory_does_not_grow_without_bound() {
        for (int i = 0; i <= AuthRateLimiter.PRUNE_ABOVE; i++) {
            limiter.tryAcquire("client-" + i);
        }
        lenient().when(clockService.now()).thenReturn(NOW.plus(AuthRateLimiter.WINDOW).plusSeconds(1));

        limiter.tryAcquire("a-client-that-is-still-here");

        Map<String, ?> remembered = (Map<String, ?>) ReflectionTestUtils.getField(limiter, "attempts");
        assertThat(remembered).containsOnlyKeys("a-client-that-is-still-here");
    }
}
