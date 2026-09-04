package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import uk.gov.hmcts.cp.domain.RequestType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReferenceGeneratorTest {

    private static final Pattern SPECIFIED = Pattern.compile("^(AR|PR)-[0-9]{4}-[A-Z0-9]{6}$");

    @Mock
    private ClockService clockService;

    private ReferenceGenerator generator() {
        when(clockService.now()).thenReturn(Instant.parse("2026-09-04T10:15:30Z"));
        return new ReferenceGenerator(clockService);
    }

    @Test
    void generating_a_reference_should_match_the_form_the_api_specifies() {
        assertThat(generator().generate(RequestType.SUBSCRIPTION)).matches(SPECIFIED);
    }

    @Test
    void subscriptions_should_be_prefixed_ar_and_publications_pr() {
        ReferenceGenerator generator = generator();

        assertThat(generator.generate(RequestType.SUBSCRIPTION)).startsWith("AR-");
        assertThat(generator.generate(RequestType.PUBLISH)).startsWith("PR-");
    }

    @Test
    void the_year_should_come_from_the_clock_so_it_can_be_fixed_in_tests() {
        assertThat(generator().generate(RequestType.SUBSCRIPTION)).contains("-2026-");
    }

    @Test
    void the_suffix_should_avoid_lower_case_so_a_reference_can_be_read_aloud() {
        String suffix = generator().generate(RequestType.SUBSCRIPTION).substring(8);

        assertThat(suffix).isUpperCase().hasSize(6);
    }

    @Test
    void ten_thousand_references_should_not_repeat() {
        ReferenceGenerator generator = generator();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.generate(RequestType.SUBSCRIPTION));
        }

        assertThat(seen).hasSize(10_000);
    }
}
