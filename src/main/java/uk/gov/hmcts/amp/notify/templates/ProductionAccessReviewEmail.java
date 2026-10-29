package uk.gov.hmcts.amp.notify.templates;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.amp.domain.NotifyEmail;

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
    private final String consumerOrganisation;
    private final LocalDate goLiveDate;
    private final String expectedCallVolumes;
    private final List<String> apis;
    private final String dataRequestDescription;
    private final String proposedOutputs;
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
            Map.entry("consumer_organisation", consumerOrganisation),
            Map.entry("go_live_date", DATE.format(goLiveDate)),
            Map.entry("expected_call_volumes", expectedCallVolumes),
            Map.entry("api_list", apis),
            Map.entry("data_request_description", dataRequestDescription),
            Map.entry("proposed_outputs", proposedOutputs));
    }
}
