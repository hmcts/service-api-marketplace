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
 * <pre>
 *   ./gradlew --stop
 *   ./gradlew test --tests '*NotifyFunctionalTest' --rerun-tasks
 * </pre>
 *
 * <p>Comment out the @Disabled to run it. Off by default because NOTIFY_API_KEY lives in a shell
 * profile and nobody wants an email every time the suite runs.
 *
 * <p>Only NOTIFY_API_KEY comes from the environment; the context is built from application.yaml,
 * so the base url is the same default the service uses. Without a key NotifyService answers 503
 * rather than quietly sending nothing.
 *
 * <p>--stop first, because the Gradle daemon hands its own environment to the forked test JVM and
 * a variable exported after it started never arrives. --rerun-tasks because environment variables
 * are not task inputs, so an unchanged source tree counts as up to date.
 *
 * <p>A Notify test key returns success and sends nothing; a team key delivers to team members
 * only, so TEST_EMAIL has to be one of them.
 */
@Disabled("Sends a real email - comment this out to run it")
@SpringBootTest
class NotifyFunctionalTest {

    private static final String TEST_EMAIL = "colin.greenwood@hmcts.net";

    @Autowired
    private NotifyService notifyService;

    @Test
    void notify_should_accept_the_template_and_its_personalisation() {
        ProductionAccessRequestEmail template = new ProductionAccessRequestEmail(
            TEST_EMAIL, "Functional Test",
            "Case Viewer",
            List.of("Scheduling and Listing Court Schedule", "Refdata Court Hearing Courthouses"),
            "PCR-FUNCTEST"
        );
        notifyService.send(template);
    }

    @Test
    void notify_should_accept_the_review_template_and_its_personalisation() {
        ProductionAccessReviewEmail template = new ProductionAccessReviewEmail(
            TEST_EMAIL,
            "Case Viewer",
            LocalDateTime.of(2026, 10, 20, 14, 30),
            "Functional Test",
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

    /**
     * An application can be asked about before it is subscribed to anything, and only Notify can
     * say whether an empty list reads as a present placeholder or a missing one.
     */
    @Test
    void notify_should_accept_an_empty_api_list() {
        ProductionAccessRequestEmail template = new ProductionAccessRequestEmail(
            TEST_EMAIL, "Functional Test",
            "Case Viewer",
            List.of(),
            "PCR-FUNCNONE"
        );
        notifyService.send(template);
    }
}
