package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.NotifyTestRequest;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessRequestEmail;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.services.NotifyService;
import uk.gov.hmcts.amp.services.UserService;

import java.util.List;
import java.util.Map;

/**
 * Temporary. Fires the production access request email at the signed-in user so the template can
 * be seen while its wording is still changing, without a form or a real request behind it.
 *
 * <p>Goes when POST /production-requests sends the email itself.
 *
 * <p>It only ever sends to the caller's own stored address, never one in the body: an endpoint
 * that emails anywhere on request is a way to send mail from HMCTS's Notify account to strangers.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notify-test")
public class NotifyTestController {

    private static final String REQUESTING_USER_ID = "requestingUserId";

    private final NotifyService notify;
    private final UserService users;

    @PostMapping
    public Map<String, Object> sendToMyself(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                            @RequestBody(required = false) final NotifyTestRequest request) {
        final UserEntity caller = users.requireSignedIn(requestingUserId);
        final ProductionAccessRequestEmail email = new ProductionAccessRequestEmail(
            caller.getEmail(),
            orElse(request == null ? null : request.consumerName(), displayName(caller)),
            orElse(request == null ? null : request.applicationName(), "Case Viewer"),
            request == null || request.apis() == null || request.apis().isEmpty()
                ? List.of("Court Schedule", "Hearing Results") : request.apis(),
            orElse(request == null ? null : request.reference(), "PCR-TESTSEND"));

        log.info("Sending the production access request template to the caller, for template checking");
        notify.send(email);

        return Map.of("templateId", email.getTemplateId(),
            "consumerName", email.getConsumerName(),
            "applicationName", email.getApplicationName(),
            "apis", email.getApis(),
            "reference", email.getReference());
    }

    private static String displayName(final UserEntity user) {
        final String name = (user.getFirstName() + " " + user.getLastName()).trim();
        return name.isEmpty() ? "Marketplace User" : name;
    }

    private static String orElse(final String given, final String fallback) {
        return given == null || given.isBlank() ? fallback : given;
    }
}
