package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void hashing_should_produce_a_bcrypt_hash_that_is_not_the_password() {
        String hash = passwordService.hash("correct-horse-battery-staple");

        assertThat(hash).startsWith("$2").doesNotContain("correct-horse");
        assertThat(hash).contains("$12$");
    }

    @Test
    void the_same_password_should_hash_differently_each_time() {
        assertThat(passwordService.hash("correct-horse-battery-staple"))
            .isNotEqualTo(passwordService.hash("correct-horse-battery-staple"));
    }

    @Test
    void the_right_password_should_match_and_a_wrong_one_should_not() {
        String hash = passwordService.hash("correct-horse-battery-staple");

        assertThat(passwordService.matches("correct-horse-battery-staple", hash)).isTrue();
        assertThat(passwordService.matches("correct-horse-battery-stapl3", hash)).isFalse();
    }

    @Test
    void stored_value_that_is_not_a_bcrypt_hash_should_never_match() {
        // The seeded accounts carry this placeholder, so none of them can be signed in to.
        assertThat(passwordService.matches("CHANGE_ME", "CHANGE_ME")).isFalse();
        assertThat(passwordService.matches("anything-at-all-1", "CHANGE_ME")).isFalse();
    }

    @Test
    void password_over_72_bytes_should_not_match_rather_than_blow_up() {
        String hash = passwordService.hash("correct-horse-battery-staple");

        assertThat(passwordService.matches("x".repeat(73), hash)).isFalse();
    }

    @Test
    void the_72_byte_limit_should_count_bytes_not_characters() {
        assertThat(PasswordService.isTooLong("x".repeat(72))).isFalse();
        assertThat(PasswordService.isTooLong("x".repeat(73))).isTrue();
        // 37 two-byte characters is 37 characters but 74 bytes.
        assertThat(PasswordService.isTooLong("é".repeat(37))).isTrue();
        assertThat(PasswordService.isTooLong("é".repeat(36))).isFalse();
    }

    @Test
    void spending_time_as_if_checking_should_work_for_any_password_including_an_over_long_one() {
        passwordService.spendTimeAsIfChecking("whatever-someone-typed");
        passwordService.spendTimeAsIfChecking("x".repeat(500));
    }
}
