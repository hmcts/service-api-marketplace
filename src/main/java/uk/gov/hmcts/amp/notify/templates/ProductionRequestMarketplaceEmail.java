package uk.gov.hmcts.amp.notify.templates;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.amp.domain.Answer;
import uk.gov.hmcts.amp.domain.CallVolume;
import uk.gov.hmcts.amp.domain.NotifyEmail;
import uk.gov.hmcts.amp.domain.OrganisationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tells the API Marketplace Team that a request has arrived. It carries no triage answers: the
 * Data Access Team decide the outcome, and this is the team's prompt to expect one.
 */
@Getter
@RequiredArgsConstructor
public class ProductionRequestMarketplaceEmail implements NotifyEmail {

    public static final String TEMPLATE_ID = "5ac4b9d1-3828-4605-ac05-421b4610cd38";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mma", Locale.UK);

    private final String emailAddress;
    private final String applicationName;
    private final LocalDateTime submittedAt;
    private final String consumerName;
    private final String consumerEmailAddress;
    private final OrganisationType organisationType;
    private final String consumerTeam;
    private final LocalDate goLiveDate;
    private final CallVolume expectedCallVolumes;
    private final List<String> apis;
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
            Map.entry("organisation_type", label(organisationType)),
            Map.entry("consumer_team", consumerTeam),
            Map.entry("go_live_date", DATE.format(goLiveDate)),
            Map.entry("expected_call_volumes", label(expectedCallVolumes)),
            Map.entry("api_list", apis));
    }

    /** Unanswered reaches Notify as empty, for a conditional block in the template to hide. */
    private static String label(final Answer answer) {
        return answer == null ? "" : answer.label();
    }
}
