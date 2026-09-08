package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashServiceTest {

    private static final String PEPPER = "test-pepper";

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final HashService hashService = new HashService(passwordEncoder, PEPPER);

    @Test
    void hashing_then_matching_the_same_password_should_succeed() {
        String hash = hashService.hash("correct horse battery staple");

        assertThat(hashService.matches("correct horse battery staple", hash)).isTrue();
    }

    @Test
    void matching_a_different_password_should_fail() {
        String hash = hashService.hash("correct horse battery staple");

        assertThat(hashService.matches("Correct horse battery staple", hash)).isFalse();
    }

    @Test
    void hashing_the_same_password_twice_should_give_different_hashes() {
        String first = hashService.hash("same password");
        String second = hashService.hash("same password");

        assertThat(first).isNotEqualTo(second);
        assertThat(hashService.matches("same password", first)).isTrue();
        assertThat(hashService.matches("same password", second)).isTrue();
    }

    @Test
    void hashing_a_password_should_record_the_algorithm_that_produced_it() {
        assertThat(hashService.hash("any password")).startsWith("{bcrypt}");
    }

    @Test
    void matching_a_hash_made_under_a_different_pepper_should_fail() {
        String hash = new HashService(passwordEncoder, "a-different-pepper").hash("password");

        assertThat(hashService.matches("password", hash)).isFalse();
    }

    @Test
    void hashing_a_password_longer_than_bcrypts_limit_should_use_all_of_it() {
        String prefix = "x".repeat(72);
        String hash = hashService.hash(prefix + "-ending-one");

        assertThat(hashService.matches(prefix + "-ending-two", hash)).isFalse();
        assertThat(hashService.matches(prefix + "-ending-one", hash)).isTrue();
    }

    @Test
    void matching_against_the_seeded_change_me_sentinel_should_fail_without_throwing() {
        assertThat(hashService.matches("anything", "CHANGE_ME")).isFalse();
    }

    @Test
    void matching_against_a_null_or_empty_hash_should_fail_without_throwing() {
        assertThat(hashService.matches("anything", null)).isFalse();
        assertThat(hashService.matches("anything", "")).isFalse();
        assertThat(hashService.matches(null, hashService.hash("password"))).isFalse();
    }

    @Test
    void constructing_without_a_pepper_should_fail_fast() {
        assertThatThrownBy(() -> new HashService(passwordEncoder, "  "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("marketplace-USER-PASSWORD-PEPPER");
    }
}
