package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.amp.domain.ProductionAccessRequestEmail;
import uk.gov.hmcts.amp.services.NotifyService;

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
            List.of("Court Schedule", "Hearing Results"),
            "PCR-FUNCTEST"
        );
        notifyService.send(template);
    }
}
