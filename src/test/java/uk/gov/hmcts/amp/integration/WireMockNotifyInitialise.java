package uk.gov.hmcts.amp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.context.ApplicationContextInitializer;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessRequestEmail;
import org.springframework.context.ConfigurableApplicationContext;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** WireMock in place of GOV.UK Notify, so the real client's HTTP is exercised without a key. */
public class WireMockNotifyInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String SEND_EMAIL = "/v2/notifications/email";
    public static final String TEMPLATE_ID = ProductionAccessRequestEmail.TEMPLATE_ID;
    public static final String REVIEW_INBOX = "api-marketplace-panel@hmcts.net";

    // Notify parses the service id and secret out of the key by position, so it has to be shaped
    // like a real one even though nothing ever checks it here.
    private static final String API_KEY = "amptest-00000000-0000-0000-0000-000000000000"
        + "-11111111-1111-1111-1111-111111111111";

    private static final WireMockServer WIREMOCK_NOTIFY = new WireMockServer(wireMockConfig().dynamicPort());

    public static WireMockServer wireMockNotify() {
        return WIREMOCK_NOTIFY;
    }

    public static void stubAccepted() {
        WIREMOCK_NOTIFY.stubFor(post(urlPathEqualTo(SEND_EMAIL)).willReturn(
            aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                {"id":"22222222-2222-2222-2222-222222222222","reference":"case-viewer",
                 "content":{"body":"body","subject":"subject"},
                 "template":{"id":"%s","version":1,"uri":"https://notify.example/v2/template"},
                 "uri":"https://notify.example/v2/notifications/22222222-2222-2222-2222-222222222222"}"""
                .formatted(TEMPLATE_ID))));
    }

    public static void stubRefused(final int status, final String message) {
        WIREMOCK_NOTIFY.stubFor(post(urlPathEqualTo(SEND_EMAIL)).willReturn(
            aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("{\"errors\":[{\"error\":\"BadRequestError\",\"message\":\"" + message + "\"}]}")));
    }

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        if (!WIREMOCK_NOTIFY.isRunning()) {
            WIREMOCK_NOTIFY.start();
        }
        final NotificationClientApi client = new NotificationClient(API_KEY, WIREMOCK_NOTIFY.baseUrl());
        applicationContext.getBeanFactory().registerSingleton("notificationClient", client);
    }
}
