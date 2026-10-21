package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.NotifyProperties;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessRequestEmail;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotifyServiceTest {

    private static final String TEMPLATE = ProductionAccessRequestEmail.TEMPLATE_ID;

    @Mock
    private NotifyProperties config;

    @Mock
    private ObjectProvider<NotificationClientApi> clientProvider;

    @Mock
    private NotificationClientApi notifyClient;

    @InjectMocks
    private NotifyService notifyService;

    @Test
    void sending_should_pass_the_template_the_address_and_the_three_personalisations() throws Exception {
        givenConfigured();
        SendEmailResponse accepted = mock(SendEmailResponse.class);
        when(accepted.getNotificationId()).thenReturn(UUID.randomUUID());
        when(notifyClient.sendEmail(any(), any(), any(), any())).thenReturn(accepted);

        notifyService.send(request());

        // api_list stays a List so Notify renders it as bullets; a joined String would not match.
        verify(notifyClient).sendEmail(TEMPLATE, "ada.lovelace@hmcts.net",
            Map.of("consumer_name", "Ada Lovelace",
                "application_name", "Case Viewer",
                "api_list", List.of("Court Schedule", "Hearing Results")),
            "case-viewer");
    }

    @Test
    void no_api_key_should_be_unavailable_rather_than_a_silent_no_op() throws Exception {
        when(config.configured()).thenReturn(false);

        assertThatThrownBy(() -> notifyService.send(request()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
            .hasMessageContaining("NOTIFY_API_KEY");

        verify(notifyClient, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void key_with_no_client_bean_should_be_unavailable_rather_than_a_null_dereference() throws Exception {
        when(config.configured()).thenReturn(true);
        when(clientProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> notifyService.send(request()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(notifyClient, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void notify_refusing_should_be_a_bad_gateway_without_leaking_its_detail() throws Exception {
        givenConfigured();
        when(notifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(new NotificationClientException("team-only key: 400 BadRequestError"));

        assertThatThrownBy(() -> notifyService.send(request()))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(e.getReason()).doesNotContain("BadRequestError");
            });
    }

    private void givenConfigured() {
        when(config.configured()).thenReturn(true);
        when(clientProvider.getIfAvailable()).thenReturn(notifyClient);
    }

    private ProductionAccessRequestEmail request() {
        return new ProductionAccessRequestEmail("ada.lovelace@hmcts.net", "Ada Lovelace", "Case Viewer",
            List.of("Court Schedule", "Hearing Results"), "case-viewer");
    }
}
