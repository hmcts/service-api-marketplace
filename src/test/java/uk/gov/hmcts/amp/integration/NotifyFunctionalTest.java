package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessRequestEmail;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessReviewEmail;
import uk.gov.hmcts.amp.services.NotifyService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends a real email through GOV.UK Notify, which is the only way to see that the template
 * accepts our personalisation: the integration test proves what goes on the wire, not that
 * Notify is happy with it.
 *
 * <p>Comment out the @Disabled to run it locally.
 *
 * <p>Only NOTIFY_API_KEY comes from the environment; the context is built from application.yaml,
 * so the base url is the same default the service uses. Without a key NotifyService answers 503
 * rather than quietly sending nothing.
 *
 */
@Disabled("Sends a real email - comment this out to run it")
@SpringBootTest
class NotifyFunctionalTest {

    private static final String TEST_EMAIL = "colin.greenwood@hmcts.net";

    @Autowired
    private NotifyService notifyService;

    @Test
    void notify_should_send_email_to_requestor() {
        ProductionAccessRequestEmail template = new ProductionAccessRequestEmail(
            TEST_EMAIL, "Functional Test",
            "Case Viewer",
            List.of("Scheduling and Listing Court Schedule", "Refdata Court Hearing Courthouses"),
            "PCR-FUNCTEST"
        );
        notifyService.send(template);
    }

    @Test
    void notify_should_send_email_to_reviewers() {
        ProductionAccessReviewEmail template = new ProductionAccessReviewEmail(
            TEST_EMAIL,
            "Case Viewer",
            LocalDateTime.of(2026, 10, 20, 14, 30),
            "Functional Test",
            TEST_EMAIL,
            "Example Legal Ltd",
            LocalDate.of(2027, 3, 27),
            "Fewer than 1,000 calls a day",
            List.of("Scheduling and Listing Court Schedule", "Refdata Court Hearing Courthouses"),
            "Hearing outcomes for listed cases, to populate a case tracker.",
            "A weekly outcomes report shared with the instructing solicitor.",
            "PCR-FUNCTEST"
        );
        notifyService.send(template);
    }
}
