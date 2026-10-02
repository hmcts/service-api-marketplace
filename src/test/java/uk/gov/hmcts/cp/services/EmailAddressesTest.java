package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class EmailAddressesTest {

    @Test
    void ordinary_addresses_should_be_accepted() {
        assertThat(EmailAddresses.isValid("joe.bloggs@example.com")).isTrue();
        assertThat(EmailAddresses.isValid("a@b.co")).isTrue();
        assertThat(EmailAddresses.isValid("first+tag@mail.example.co.uk")).isTrue();
        assertThat(EmailAddresses.isValid("o'brien@example.com")).isTrue();
    }

    @Test
    void malformed_addresses_should_be_rejected() {
        assertThat(EmailAddresses.isValid(null)).isFalse();
        assertThat(EmailAddresses.isValid("")).isFalse();
        assertThat(EmailAddresses.isValid("plain")).isFalse();
        assertThat(EmailAddresses.isValid("no-at.example.com")).isFalse();
        assertThat(EmailAddresses.isValid("two@@example.com")).isFalse();
        assertThat(EmailAddresses.isValid("a@b@example.com")).isFalse();
        assertThat(EmailAddresses.isValid("no-dot@example")).isFalse();
        assertThat(EmailAddresses.isValid("has space@example.com")).isFalse();
        assertThat(EmailAddresses.isValid("a@exa mple.com")).isFalse();
        assertThat(EmailAddresses.isValid("@example.com")).isFalse();
        assertThat(EmailAddresses.isValid("a@.com")).isFalse();
    }

    @Test
    void empty_parts_of_the_domain_should_be_rejected() {
        assertThat(EmailAddresses.isValid("a@example..com")).isFalse();
        assertThat(EmailAddresses.isValid("a@example.com.")).isFalse();
        assertThat(EmailAddresses.isValid("a@.example.com")).isFalse();
    }

    @Test
    void the_length_limit_should_be_254_inclusive() {
        String suffix = "@example.com";
        assertThat(EmailAddresses.isValid("a".repeat(EmailAddresses.MAX_LENGTH - suffix.length()) + suffix)).isTrue();
        assertThat(EmailAddresses.isValid("a".repeat(EmailAddresses.MAX_LENGTH - suffix.length() + 1) + suffix))
            .isFalse();
    }

    @Test
    void huge_string_should_be_turned_away_at_once_not_matched() {
        // The input CodeQL's alert describes, made large enough that the old pattern, run first, would
        // have taken far longer than this limit. The length check comes first, so it is instant.
        String hostile = "!@!." + "!.".repeat(500_000);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
            assertThat(EmailAddresses.isValid(hostile)).isFalse());
    }

    @Test
    void crafted_string_just_inside_the_limit_should_be_answered_quickly() {
        // 254 characters, so it does reach the pattern; it ends in a dot, so it is not a valid address.
        String hostile = "!@!." + "!.".repeat(125);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
            assertThat(EmailAddresses.isValid(hostile)).isFalse());
    }
}
