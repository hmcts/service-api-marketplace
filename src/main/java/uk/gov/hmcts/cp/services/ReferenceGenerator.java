package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.ZoneOffset;

import uk.gov.hmcts.cp.domain.RequestType;

@Service
@RequiredArgsConstructor
public class ReferenceGenerator {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final int SUFFIX_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClockService clockService;

    public String generate(final RequestType type) {
        return String.format("%s-%d-%s", type.getReferencePrefix(), year(), suffix());
    }

    private int year() {
        return clockService.now().atZone(ZoneOffset.UTC).getYear();
    }

    private String suffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return suffix.toString();
    }
}
