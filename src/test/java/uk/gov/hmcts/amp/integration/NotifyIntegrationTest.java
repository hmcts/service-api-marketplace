package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.ProductionAccessEmail;
import uk.gov.hmcts.amp.services.NotifyService;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.SEND_EMAIL;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.TEMPLATE_ID;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.stubAccepted;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.stubRefused;
import static uk.gov.hmcts.amp.integration.WireMockNotifyInitialise.wireMockNotify;

/** The real Notify client against WireMock, so what goes on the wire is what Notify would get. */
@SpringBootTest(properties = {
    "notify.api-key=amptest-00000000-0000-0000-0000-000000000000-11111111-1111-1111-1111-111111111111",
    "notify.production-access-template-id=" + TEMPLATE_ID})
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockNotifyInitialise.class})
class NotifyIntegrationTest {

    @Autowired
    private NotifyService notify;

    @BeforeEach
    void forgetPreviousStubs() {
        wireMockNotify().resetAll();
    }

    @Test
    void the_request_body_should_carry_the_template_the_address_and_the_personalisation() throws Exception {
        stubAccepted();

        notify.productionAccessRequested(request());

        wireMockNotify().verify(postRequestedFor(urlPathEqualTo(SEND_EMAIL))
            .withRequestBody(equalToJson("""
                {"template_id":"%s","email_address":"ada.lovelace@hmcts.net","reference":"case-viewer",
                 "personalisation":{"consumer_name":"Ada Lovelace","application_name":"Case Viewer",
                 "api_list":["Court Schedule","Hearing Results"]}}""".formatted(TEMPLATE_ID), true, true)));
    }

    @Test
    void notify_refusing_should_be_a_bad_gateway_that_names_nothing_of_notifys() {
        stubRefused(400, "email_address Not a valid email address");

        assertThatThrownBy(() -> notify.productionAccessRequested(request()))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).doesNotContain("email_address");
            });
    }

    private ProductionAccessEmail request() {
        return new ProductionAccessEmail("ada.lovelace@hmcts.net", "Ada Lovelace", "Case Viewer",
            List.of("Court Schedule", "Hearing Results"), "case-viewer");
    }
}
