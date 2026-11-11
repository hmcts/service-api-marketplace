package uk.gov.hmcts.amp.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The enums carry the wording a reviewer reads, and the request record validates against the same
 * values. These pin the two together: an answer added to one without the other fails here rather
 * than reaching a reviewer as a blank line.
 */
class AnswerTest {

    private static Stream<Answer[]> everyAnswer() {
        return Stream.of(OrganisationType.values(), DataRecipient.values(),
                DataCombined.values(), CallVolume.values())
            .flatMap(Arrays::stream)
            .map(answer -> new Answer[] {answer});
    }

    @ParameterizedTest
    @MethodSource("everyAnswer")
    void every_answer_should_carry_a_slug_and_wording_a_reviewer_can_read(final Answer answer) {
        assertThat(answer.slug()).isNotBlank().matches("[a-z][a-z-]*");
        assertThat(answer.label()).isNotBlank().isNotEqualTo(answer.slug());
    }

    @Test
    void the_validation_pattern_should_name_exactly_the_values_the_enum_holds() {
        assertThat(slugsIn(ProductionAccessRequest.ORGANISATION_TYPES))
            .containsExactlyInAnyOrderElementsOf(slugsOf(OrganisationType.values()));
        assertThat(slugsIn(ProductionAccessRequest.DATA_RECIPIENTS))
            .containsExactlyInAnyOrderElementsOf(slugsOf(DataRecipient.values()));
        assertThat(slugsIn(ProductionAccessRequest.DATA_COMBINED))
            .containsExactlyInAnyOrderElementsOf(slugsOf(DataCombined.values()));
        assertThat(slugsIn(ProductionAccessRequest.CALL_VOLUMES))
            .containsExactlyInAnyOrderElementsOf(slugsOf(CallVolume.values()));
    }

    @Test
    void slugs_the_enum_does_not_hold_should_say_so_rather_than_read_as_blank() {
        assertThatThrownBy(() -> DataRecipient.fromSlug("farm-animals"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DataRecipient")
            .hasMessageContaining("farm-animals");
    }

    @Test
    void an_unanswered_question_should_map_to_nothing_rather_than_fail() {
        assertThat(OrganisationType.fromSlug(null)).isNull();
        assertThat(DataRecipient.fromSlugs(null)).isEmpty();
    }

    @Test
    void unsure_should_need_describing_in_the_same_way_as_yes() {
        assertThat(DataCombined.YES.needsDescribing()).isTrue();
        assertThat(DataCombined.UNSURE.needsDescribing()).isTrue();
        assertThat(DataCombined.NO.needsDescribing()).isFalse();
    }

    private static List<String> slugsIn(final String pattern) {
        return List.of(Pattern.compile("\\|").split(pattern));
    }

    private static List<String> slugsOf(final Answer[] answers) {
        return Arrays.stream(answers).map(Answer::slug).toList();
    }
}
