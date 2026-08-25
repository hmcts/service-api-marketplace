package uk.gov.hmcts.cp.services;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ClockService {

    private final Clock clock;

    public ClockService(final Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public OffsetDateTime nowOffsetUTC() {
        return OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
