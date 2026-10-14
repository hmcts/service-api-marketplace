package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDomainTest {

    @Test
    void domain_should_be_lower_case_and_trimmed() {
        assertThat(EmailDomain.of("  Zaheer.Iqbal@HMCTS.Net ")).contains("hmcts.net");
    }

    @Test
    void addresses_without_exactly_one_at_sign_have_no_domain() {
        assertThat(EmailDomain.of(null)).isEmpty();
        assertThat(EmailDomain.of("no-at-sign.example.com")).isEmpty();
        assertThat(EmailDomain.of("@example.com")).isEmpty();
        assertThat(EmailDomain.of("two@@example.com")).isEmpty();
        assertThat(EmailDomain.of("a@b@example.com")).isEmpty();
    }

    @Test
    void domains_must_look_like_domains() {
        assertThat(EmailDomain.normalise("Example.GOV.uk")).contains("example.gov.uk");
        assertThat(EmailDomain.normalise("localhost")).isEmpty();
        assertThat(EmailDomain.normalise(".example.com")).isEmpty();
        assertThat(EmailDomain.normalise("example.com.")).isEmpty();
        assertThat(EmailDomain.normalise("exa mple.com")).isEmpty();
        assertThat(EmailDomain.normalise("*.example.com")).isEmpty();
        assertThat(EmailDomain.normalise(null)).isEmpty();
        assertThat(EmailDomain.normalise("a.b")).isEmpty();
    }

    @Test
    void shared_mail_providers_are_recognised() {
        assertThat(EmailDomain.isShared("gmail.com")).isTrue();
        assertThat(EmailDomain.isShared("justice.gov.uk")).isFalse();
    }
}
