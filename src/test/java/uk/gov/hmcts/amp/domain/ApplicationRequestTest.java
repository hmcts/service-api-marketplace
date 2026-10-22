package uk.gov.hmcts.amp.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRequestTest {

    @Test
    void omitting_apis_should_leave_an_empty_list_rather_than_null() {
        assertThat(new ApplicationRequest("Case Viewer", null).apis()).isEmpty();
    }

    @Test
    void supplied_apis_should_be_kept_as_they_are() {
        assertThat(new ApplicationRequest("Case Viewer", List.of("slc")).apis())
            .containsExactly("slc");
    }

    @Test
    void the_appname_should_be_lowercased_trimmed_and_hyphenated() {
        assertThat(new ApplicationRequest("  Case   Viewer  ", null).appname()).isEqualTo("case-viewer");
    }
}
