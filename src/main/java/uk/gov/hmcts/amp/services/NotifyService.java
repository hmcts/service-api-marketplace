package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.NotifyProperties;
import uk.gov.hmcts.amp.domain.ProductionAccessEmail;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.util.Map;

/**
 * Email through GOV.UK Notify. Bodies and subjects are edited in Notify by whoever owns the
 * wording, so this carries template ids and personalisation and never any content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotifyProperties config;
    private final ObjectProvider<NotificationClientApi> client;

    public void productionAccessRequested(final ProductionAccessEmail email) {
        final Map<String, Object> personalisation = Map.of(
            "consumer_name", email.consumerName(),
            "application_name", email.applicationName(),
            "api_list", email.apis());
        send(config.productionAccessTemplateId(), email.emailAddress(), personalisation, email.reference());
    }

    private void send(final String templateId, final String emailAddress,
                      final Map<String, Object> personalisation, final String reference) {
        final NotificationClientApi notify = requireConfigured();
        try {
            final SendEmailResponse sent = notify.sendEmail(templateId, emailAddress, personalisation, reference);
            log.info("Notify accepted template {} as notification {}", templateId, sent.getNotificationId());
        } catch (final NotificationClientException refused) {
            // Deliberately thrown rather than swallowed: whether a failed email should fail the
            // caller's operation is the caller's decision, not one to make silently here.
            log.error("Notify refused template {} for reference {}", templateId, reference, refused);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the email.");
        }
    }

    private NotificationClientApi requireConfigured() {
        final NotificationClientApi notify = config.configured() ? client.getIfAvailable() : null;
        if (notify == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "NOTIFY_API_KEY is not configured in this environment.");
        }
        return notify;
    }
}
