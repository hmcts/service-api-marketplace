package uk.gov.hmcts.amp.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessRequestEmail;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessReviewEmail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service tests mock this away, so every field a template carries is pinned here, by the
 * personalisation key Notify looks for rather than by our own field name.
 */
class NotifyMapperTest {

    private static final List<String> APIS =
        List.of("Scheduling and Listing Court Schedule", "Refdata Court Hearing Courthouses");
    private static final String INBOX = "api-marketplace-panel@hmcts.net";

    private final NotifyMapper notifyMapper = new NotifyMapperImpl();

    @Test
    void the_requester_email_should_go_to_the_requester_with_their_own_details() {
        ProductionAccessRequestEmail email =
            notifyMapper.toRequesterEmail(request(), user(), APIS);

        assertThat(email.getEmailAddress()).isEqualTo("ada.lovelace@hmcts.net");
        assertThat(email.getReference()).isEqualTo("PCR-ABCD2345");
        assertThat(email.getTemplateId()).isEqualTo(ProductionAccessRequestEmail.TEMPLATE_ID);
        assertThat(email.getPersonalisation()).containsOnly(
            Map.entry("consumer_name", "Ada Lovelace"),
            Map.entry("application_name", "Case Viewer"),
            Map.entry("api_list", APIS));
    }

    @Test
    void the_review_email_should_go_to_the_inbox_and_carry_every_answer() {
        ProductionAccessReviewEmail email =
            notifyMapper.toReviewEmail(request(), user(), APIS, INBOX);

        assertThat(email.getEmailAddress()).isEqualTo(INBOX);
        assertThat(email.getReference()).isEqualTo("PCR-ABCD2345");
        assertThat(email.getTemplateId()).isEqualTo(ProductionAccessReviewEmail.TEMPLATE_ID);
        assertThat(email.getPersonalisation()).containsOnly(
            Map.entry("application_name", "Case Viewer"),
            Map.entry("submitted_date", "19 October 2026"),
            Map.entry("submitted_time", "2:30pm"),
            Map.entry("consumer_name", "Ada Lovelace"),
            Map.entry("consumer_email_address", "ada.lovelace@hmcts.net"),
            Map.entry("consumer_organisation", "Example Legal Ltd"),
            Map.entry("go_live_date", "27 March 2027"),
            Map.entry("expected_call_volumes", "low"),
            Map.entry("api_list", APIS),
            Map.entry("data_request_description", "Hearing outcomes for listed cases."),
            Map.entry("proposed_outputs", "A weekly outcomes report."));
    }

    @Test
    void the_requester_should_never_be_copied_on_the_review_email() {
        ProductionAccessReviewEmail email =
            notifyMapper.toReviewEmail(request(), user(), APIS, INBOX);

        assertThat(email.getEmailAddress()).isNotEqualTo(user().getEmail());
    }

    @Test
    void the_api_list_should_stay_a_list_so_notify_renders_it_as_bullets() {
        // A joined String reaches Notify as one line of prose instead of bullet points.
        assertThat(notifyMapper.toRequesterEmail(request(), user(), APIS)
            .getPersonalisation().get("api_list")).isInstanceOf(List.class);
        assertThat(notifyMapper.toReviewEmail(request(), user(), APIS, INBOX)
            .getPersonalisation().get("api_list")).isInstanceOf(List.class);
    }

    @Test
    void user_with_no_surname_recorded_should_not_be_greeted_with_a_trailing_space() {
        UserEntity partial = user();
        partial.setLastName(null);

        assertThat(notifyMapper.toRequesterEmail(request(), partial, APIS)
            .getPersonalisation()).containsEntry("consumer_name", "Ada");
    }

    @Test
    void an_application_with_no_subscriptions_should_map_to_an_empty_list_not_a_missing_key() {
        assertThat(notifyMapper.toRequesterEmail(request(), user(), List.of())
            .getPersonalisation()).containsEntry("api_list", List.of());
        assertThat(notifyMapper.toReviewEmail(request(), user(), List.of(), INBOX)
            .getPersonalisation()).containsEntry("api_list", List.of());
    }

    @Test
    void every_personalisation_key_should_be_snake_case_as_the_templates_spell_them() {
        // Notify matches a placeholder literally, so ((application name)) and ((application_name))
        // are two fields and the one nobody fills fails the whole send with a 400.
        Stream.of(notifyMapper.toRequesterEmail(request(), user(), APIS),
                notifyMapper.toReviewEmail(request(), user(), APIS, INBOX))
            .flatMap(email -> email.getPersonalisation().keySet().stream())
            .forEach(key -> assertThat(key).matches("[a-z][a-z0-9_]*"));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setEmail("ada.lovelace@hmcts.net");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        return user;
    }

    private ProductionRequestEntity request() {
        ProductionRequestEntity request = new ProductionRequestEntity();
        request.setReference("PCR-ABCD2345");
        request.setApplicationName("Case Viewer");
        request.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 14, 30));
        request.setOrganisation("Example Legal Ltd");
        request.setGoLiveDate(LocalDate.of(2027, 3, 27));
        request.setCallVolume("low");
        request.setDataRequest("Hearing outcomes for listed cases.");
        request.setProposedOutputs("A weekly outcomes report.");
        return request;
    }
}
