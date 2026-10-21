package uk.gov.hmcts.amp.notify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.notify.EmailMask.masked;

class EmailMaskTest {

    @Test
    void name_in_parts_should_keep_an_initial_for_each_and_the_whole_domain() {
        assertThat(masked("colin.greenwood@hmcts.net")).isEqualTo("c.g@hmcts.net");
    }

    @Test
    void single_word_mailbox_should_keep_one_initial() {
        assertThat(masked("APIMarketplace@hmcts.net")).isEqualTo("A@hmcts.net");
    }

    @Test
    void anything_that_is_not_an_address_should_say_nothing_at_all() {
        assertThat(masked(null)).isEqualTo("***");
        assertThat(masked("")).isEqualTo("***");
        assertThat(masked("not-an-address")).isEqualTo("***");
        // No local part to take an initial from, so there is nothing to show but the domain.
        assertThat(masked("@hmcts.net")).isEqualTo("***");
    }

    @Test
    void the_local_part_should_never_survive_in_full() {
        assertThat(masked("DataAccessPanel@justice.gov.uk")).doesNotContain("DataAccessPanel");
    }
}
