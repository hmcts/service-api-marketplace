package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.amp.domain.CallVolume;
import uk.gov.hmcts.amp.domain.DataCombined;
import uk.gov.hmcts.amp.domain.DataRecipient;
import uk.gov.hmcts.amp.domain.OrganisationType;
import uk.gov.hmcts.amp.notify.templates.ProductionRequestConsumerEmail;
import uk.gov.hmcts.amp.notify.templates.ProductionRequestPanelEmail;
import uk.gov.hmcts.amp.notify.templates.ProductionRequestMarketplaceEmail;
import uk.gov.hmcts.amp.services.NotifyService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends a real email through GOV.UK Notify, which is the only way to see that the template
 * accepts our personalisation: the integration test proves what goes on the wire, not that
 * Notify is happy with it.
 *
 * <p>Runs on a Mac only, so it stays out of CI.
 *
 * <p>Only NOTIFY_API_KEY comes from the environment; the context is built from application.yaml,
 * so the base url is the same default the service uses. Without a key NotifyService answers 503
 * rather than quietly sending nothing.
 *
 */
@EnabledOnOs(OS.MAC)
@SpringBootTest
class NotifyFunctionalTest {

    private static final String TEST_EMAIL = "colin.greenwood@hmcts.net";

    @Autowired
    private NotifyService notifyService;

    @Test
    void notify_should_send_email_to_requestor() {
        ProductionRequestConsumerEmail template = new ProductionRequestConsumerEmail(
            TEST_EMAIL, "Functional Test",
            "Case Viewer",
            List.of("Scheduling and Listing Court Schedule", "Refdata Court Hearing Courthouses"),
            "PCR-FUNCTEST"
        );
        notifyService.send(template);
    }

    @Test
    void notify_should_send_email_to_reviewers() {
        ProductionRequestPanelEmail template = new ProductionRequestPanelEmail(
            TEST_EMAIL,
            "Case Viewer",
            LocalDateTime.of(2026, 10, 20, 14, 30),
            "Functional Test",
            TEST_EMAIL,
            "Example Legal Ltd",
            LocalDate.of(2027, 3, 27),
            CallVolume.LOW,
            List.of("Scheduling and Listing Court Schedule", "Refdata Court Hearing Courthouses"),
            "Hearing outcomes for listed cases, to populate a case tracker.",
            // The form stopped asking this, so a request made now carries no answer.
            null,
            OrganisationType.EXTERNAL,
            List.of(DataRecipient.OWN_TEAM, DataRecipient.SUPPLIER),
            "Our delivery partner runs the ingest.",
            DataCombined.YES,
            "Joined to our own case tracker to measure turnaround.",
            "PCR-FUNCTEST"
        );
        notifyService.send(template);
    }

    @Test
    void notify_should_send_email_to_the_marketplace_team() {
        ProductionRequestMarketplaceEmail template = new ProductionRequestMarketplaceEmail(
            TEST_EMAIL,
            "Case Viewer",
            LocalDateTime.of(2026, 10, 20, 14, 30),
            "Functional Test",
            TEST_EMAIL,
            OrganisationType.EXTERNAL,
            "Example Legal Ltd",
            LocalDate.of(2027, 3, 27),
            CallVolume.LOW,
            List.of("Scheduling and Listing Court Schedule", "Refdata Court Hearing Courthouses"),
            "PCR-FUNCTEST"
        );
        notifyService.send(template);
    }
}
