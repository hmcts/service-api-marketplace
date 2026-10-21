package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.NotifyProperties;
import uk.gov.hmcts.amp.domain.NotifyEmail;
import uk.gov.hmcts.amp.notify.EmailMask;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;


/** Email through GOV.UK Notify. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotifyProperties config;
    private final ObjectProvider<NotificationClientApi> client;

    public void send(final NotifyEmail email) {
        final NotificationClientApi notify = requireConfigured();
        try {
            final SendEmailResponse sent = notify.sendEmail(
                email.getTemplateId(), email.getEmailAddress(),
                email.getPersonalisation(), email.getReference()
            );
            log.info(
                "Notify accepted template {} to {} as notification {}",
                email.getTemplateId(), EmailMask.masked(email.getEmailAddress()), sent.getNotificationId()
            );
        } catch (final NotificationClientException refused) {
            log.error(
                "Notify refused template {} for reference {}",
                email.getTemplateId(), email.getReference(), refused
            );
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the email.");
        }
    }

    private NotificationClientApi requireConfigured() {
        final NotificationClientApi notify = config.configured() ? client.getIfAvailable() : null;
        if (notify == null) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NOTIFY_API_KEY is not configured in this environment."
            );
        }
        return notify;
    }
}
