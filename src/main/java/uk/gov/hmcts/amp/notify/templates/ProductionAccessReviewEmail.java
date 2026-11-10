package uk.gov.hmcts.amp.notify.templates;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.amp.domain.NotifyEmail;
import uk.gov.hmcts.amp.notify.TriageLabels;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public class ProductionAccessReviewEmail implements NotifyEmail {

    public static final String TEMPLATE_ID = "d632ba87-dfde-46a8-89fc-d5377e7cf2c1";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mma", Locale.UK);

    private final String emailAddress;
    private final String applicationName;
    private final LocalDateTime submittedAt;
    private final String consumerName;
    private final String consumerEmailAddress;
    private final String consumerTeam;
    private final LocalDate goLiveDate;
    private final String expectedCallVolumes;
    private final List<String> apis;
    private final String dataRequestDescription;
    private final String proposedOutputs;
    private final String organisationType;
    private final List<String> dataRecipients;
    private final String dataRecipientsExplanation;
    private final String dataCombined;
    private final String dataCombinedDescription;
    private final String reference;

    @Override
    public String getTemplateId() {
        return TEMPLATE_ID;
    }

    @Override
    public Map<String, Object> getPersonalisation() {
        // api_list stays a List so Notify renders it as bullets.
        return Map.ofEntries(
            Map.entry("application_name", applicationName),
            Map.entry("submitted_date", DATE.format(submittedAt)),
            Map.entry("submitted_time", TIME.format(submittedAt).toLowerCase(Locale.UK)),
            Map.entry("consumer_name", consumerName),
            Map.entry("consumer_email_address", consumerEmailAddress),
            // The form asks the organisation as a choice and the team as free text, which is
            // the order the template reads them in: Organisation, then Team.
            Map.entry("organisation_type", TriageLabels.organisationType(organisationType)),
            Map.entry("consumer_team", consumerTeam),
            Map.entry("go_live_date", DATE.format(goLiveDate)),
            Map.entry("expected_call_volumes", expectedCallVolumes),
            Map.entry("api_list", apis),
            Map.entry("data_request_description", dataRequestDescription),
            // The template no longer prints this, but sending a key it does not use costs
            // nothing, where a placeholder we stop sending fails the whole send with a 400.
            Map.entry("proposed_outputs", text(proposedOutputs)),
            // Answered as slugs, sent as the wording the reviewer reads. Requests made before
            // the form asked these carry none of them, which reaches Notify as an empty value.
            Map.entry("data_recipient_list", TriageLabels.dataRecipients(dataRecipients)),
            Map.entry("data_recipients_explanation", text(dataRecipientsExplanation)),
            Map.entry("data_combined", TriageLabels.dataCombined(dataCombined)),
            Map.entry("data_combined_description", text(dataCombinedDescription)));
    }

    private static String text(final String answer) {
        return answer == null ? "" : answer;
    }
}
