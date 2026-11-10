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
import static org.assertj.core.api.InstanceOfAssertFactories.list;

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
            Map.entry("organisation_type", "Other external organisation"),
            Map.entry("consumer_team", "Example Legal Ltd"),
            Map.entry("go_live_date", "27 March 2027"),
            Map.entry("expected_call_volumes", "low"),
            Map.entry("api_list", APIS),
            Map.entry("data_request_description", "Hearing outcomes for listed cases."),
            Map.entry("proposed_outputs", "A weekly outcomes report."),
            Map.entry("data_recipient_list",
                List.of("Me/my immediate team only", "Supplier/contract/delivery partner")),
            Map.entry("data_recipients_explanation", "Our delivery partner runs the ingest."),
            Map.entry("data_combined", "Yes"),
            Map.entry("data_combined_description", "Joined to our case tracker."));
    }

    @Test
    void the_review_email_should_fill_every_placeholder_the_notify_template_prints() {
        // These names belong to the Notify template, not to us. Notify fails the whole send
        // with a 400 when a template prints a placeholder the sender left out, so a rename
        // in the template is a breaking change until this list follows it.
        assertThat(notifyMapper.toReviewEmail(request(), user(), APIS, INBOX).getPersonalisation())
            .containsKeys("application_name", "submitted_date", "submitted_time", "consumer_name",
                "consumer_email_address", "organisation_type", "consumer_team", "go_live_date",
                "expected_call_volumes", "api_list", "data_request_description", "data_recipient_list",
                "data_recipients_explanation", "data_combined", "data_combined_description");
    }

    @Test
    void the_triage_answers_should_reach_the_reviewer_as_wording_and_not_as_slugs() {
        // The reviewer reads this email. own-team tells them nothing the form's own words do not.
        Map<String, Object> personalisation =
            notifyMapper.toReviewEmail(request(), user(), APIS, INBOX).getPersonalisation();

        assertThat(personalisation).containsEntry("organisation_type", "Other external organisation");
        assertThat(personalisation).containsEntry("data_combined", "Yes");
        assertThat(personalisation.get("data_recipient_list"))
            .asInstanceOf(list(String.class))
            .containsExactly("Me/my immediate team only", "Supplier/contract/delivery partner");
    }

    @Test
    void the_recipient_list_should_stay_a_list_so_notify_renders_it_as_bullets() {
        assertThat(notifyMapper.toReviewEmail(request(), user(), APIS, INBOX)
            .getPersonalisation().get("data_recipient_list")).isInstanceOf(List.class);
    }

    @Test
    void requests_made_before_the_triage_questions_should_send_empty_answers_not_fail() {
        // Map.ofEntries rejects a null outright, so an unanswered question would lose the
        // reviewer their whole email rather than one line of it.
        ProductionRequestEntity older = request();
        older.setOrganisationType(null);
        older.setDataRecipients(null);
        older.setDataRecipientsExplanation(null);
        older.setDataCombined(null);
        older.setDataCombinedDescription(null);

        Map<String, Object> personalisation =
            notifyMapper.toReviewEmail(older, user(), APIS, INBOX).getPersonalisation();

        assertThat(personalisation).containsEntry("organisation_type", "");
        assertThat(personalisation).containsEntry("data_recipient_list", List.of());
        assertThat(personalisation).containsEntry("data_recipients_explanation", "");
        assertThat(personalisation).containsEntry("data_combined", "");
        assertThat(personalisation).containsEntry("data_combined_description", "");
    }

    @Test
    void an_answer_with_no_wording_yet_should_reach_the_reviewer_as_the_slug_itself() {
        // Better a reviewer reads "farm-animals" than that the answer vanishes from the email.
        ProductionRequestEntity ahead = request();
        ahead.setDataRecipients(List.of("own-team", "farm-animals"));

        assertThat(notifyMapper.toReviewEmail(ahead, user(), APIS, INBOX)
            .getPersonalisation().get("data_recipient_list"))
            .asInstanceOf(list(String.class))
            .containsExactly("Me/my immediate team only", "farm-animals");
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
        request.setOrganisationType("external");
        request.setDataRecipients(List.of("own-team", "supplier"));
        request.setDataRecipientsExplanation("Our delivery partner runs the ingest.");
        request.setDataCombined("yes");
        request.setDataCombinedDescription("Joined to our case tracker.");
        return request;
    }
}
