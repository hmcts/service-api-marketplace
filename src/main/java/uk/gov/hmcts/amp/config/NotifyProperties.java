package uk.gov.hmcts.amp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "notify")
public record NotifyProperties(String apiKey, String baseUrl, List<String> reviewInboxes,
                               List<String> ampTeamInboxes, String environmentName) {

    private static final String PRODUCTION = "PROD";

    /**
     * Outside production the review and team emails go to whoever made the request, so the
     * templates can be exercised without troubling the real inboxes. Anything that is not
     * production redirects, so an environment that never names itself cannot reach them.
     */
    public boolean redirectingToRequester() {
        return !PRODUCTION.equalsIgnoreCase(environmentName);
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<String> reviewers() {
        return inboxes(reviewInboxes);
    }

    /** The API Marketplace Team, who are told a request arrived rather than asked to decide it. */
    public List<String> ampTeam() {
        return inboxes(ampTeamInboxes);
    }

    private static List<String> inboxes(final List<String> configured) {
        return configured == null ? List.of()
            : configured.stream().map(String::trim).filter(inbox -> !inbox.isEmpty()).toList();
    }
}
