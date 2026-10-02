package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientSecretGeneratorTest {

    private final ClientSecretGenerator generator = new ClientSecretGenerator();

    @Test
    void secret_should_be_amp_underscore_and_48_hex_characters() {
        assertThat(generator.generate()).matches("amp_[0-9a-f]{48}");
    }

    @Test
    void secrets_should_not_repeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(2000);
    }

    @Test
    void secret_should_fit_inside_bcrypts_72_byte_limit_or_the_tail_would_be_ignored() {
        assertThat(generator.generate().getBytes(StandardCharsets.UTF_8).length)
            .isLessThanOrEqualTo(PasswordService.MAX_PASSWORD_BYTES);
    }

    @Test
    void the_preview_should_be_the_last_four_characters() {
        assertThat(ClientSecretGenerator.previewOf("amp_0123456789abcdef")).isEqualTo("cdef");
    }
}
