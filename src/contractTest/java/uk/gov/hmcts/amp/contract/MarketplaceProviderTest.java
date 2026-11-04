package uk.gov.hmcts.amp.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.domain.ApplicationResponse;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.domain.ProductionAccessResponse;
import uk.gov.hmcts.amp.domain.SessionRequest;
import uk.gov.hmcts.amp.integration.TestContainersInitialise;
import uk.gov.hmcts.amp.services.ApplicationService;
import uk.gov.hmcts.amp.services.ProductionRequestService;
import uk.gov.hmcts.amp.services.UserService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Replays every contract a consumer has published for this service against the real service:
 * its controllers, services and a Postgres, with Entra and APIM faked by local-bypass. The provider
 * states set up this service's own data and say nothing about who the consumer is.
 */
@Provider("amp_service_api_marketplace")
@PactBroker(
    url = "${pactbroker.url:https://pact-broker.platform.hmcts.net}",
    providerBranch = "${pact.provider.branch:local}",
    enablePendingPacts = "${pactbroker.enablePending:true}"
)
@IgnoreNoPactsToVerify
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "local-bypass.enabled=true")
@ContextConfiguration(initializers = TestContainersInitialise.class)
class MarketplaceProviderTest {

    private static final String EMAIL = "pact@amp.example.com";

    @LocalServerPort
    private int port;

    @Autowired
    private UserService users;

    @Autowired
    private ApplicationService applications;

    @Autowired
    private ProductionRequestService productionRequests;

    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder().matchingBranch().mainBranch().deployedOrReleased();
    }

    @BeforeEach
    void target(final PactVerificationContext context) {
        if (context != null) {
            context.setTarget(new HttpTestTarget("localhost", port));
        }
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verify(final PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    @State("a signed-in user")
    void signedIn(final Map<String, Object> params) {
        signInWithNothing(objectId(params));
    }

    @State("a signed-in user with an application")
    Map<String, Object> withApplication(final Map<String, Object> params) {
        final ApplicationResponse application = registerApplication(params);
        return Map.of("clientId", application.clientId());
    }

    @State("a signed-in user with a production request")
    void withProductionRequest(final Map<String, Object> params) {
        final ApplicationResponse application = registerApplication(params);
        productionRequests.create(objectId(params), new ProductionAccessRequest(application.clientId(),
            "Pact Test Ltd", LocalDate.now().plusYears(1), "low",
            "Court schedules for a contract test.", "None: made by a contract test."));
    }

    @State("a signed-in user with an application subscribed to an API")
    Map<String, Object> withSubscription(final Map<String, Object> params) {
        final ApplicationResponse application = registerApplication(params);
        final String name = applications.subscribe(objectId(params), application.clientId(),
            String.valueOf(params.get("shortCode"))).subscriptionName();
        return Map.of("subscriptionName", name);
    }

    private ApplicationResponse registerApplication(final Map<String, Object> params) {
        final String objectId = objectId(params);
        signInWithNothing(objectId);
        return applications.create(objectId, new ApplicationRequest(String.valueOf(params.get("name")), List.of()));
    }

    /** Signs the user in and removes what earlier interactions left, so each starts from the same place. */
    private void signInWithNothing(final String objectId) {
        users.signIn(new SessionRequest(objectId, EMAIL, "Pact Test"));
        productionRequests.list(objectId).stream()
            .map(ProductionAccessResponse::reference)
            .forEach(reference -> productionRequests.delete(objectId, reference));
        applications.list(objectId)
            .forEach(application -> applications.delete(objectId, application.clientId()));
    }

    private static String objectId(final Map<String, Object> params) {
        return String.valueOf(params.get("objectId"));
    }
}
