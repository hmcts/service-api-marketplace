package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.audit.AuditAction;
import uk.gov.hmcts.amp.audit.LogAudit;
import uk.gov.hmcts.amp.domain.ApiSubscriptionRequest;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.services.SignedInCaller;
import uk.gov.hmcts.amp.openapi.api.SubscriptionKeysApi;
import uk.gov.hmcts.amp.openapi.model.ApiSubscription;
import uk.gov.hmcts.amp.openapi.model.SubscriptionKey;
import uk.gov.hmcts.amp.services.ApimSubscriptionKeyService;
import uk.gov.hmcts.amp.services.ApplicationService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscriptionKeyController implements SubscriptionKeysApi {

    private final ApimSubscriptionKeyService subscriptionKeys;
    private final ApplicationService applications;
    private final ResponseMapper mapper;
    private final SignedInCaller caller;

    @Override
    public List<SubscriptionKey> listSubscriptionKeys() {
        log.info("List APIM subscription keys");
        return mapper.toSubscriptionKeys(subscriptionKeys.list(caller.objectId()));
    }

    @Override
    @LogAudit(action = AuditAction.CONSUMER_SUBSCRIPTION_KEY_CREATED, target = "#result?.subscriptionName",
        api = "#request.shortCode()")
    public ApiSubscription createSubscriptionKey(final ApiSubscriptionRequest request) {
        log.info("Subscribe application {} to {}", request.clientId(), request.shortCode());
        return mapper.toApiSubscription(
            applications.subscribe(caller.objectId(), request.clientId(), request.shortCode()));
    }

    @Override
    @LogAudit(action = AuditAction.CONSUMER_SUBSCRIPTION_KEY_VALUE_READ, target = "#name")
    public SubscriptionKey getSubscriptionKeyValues(final String name) {
        log.info("Read APIM subscription key values for {}", name);
        return mapper.toSubscriptionKey(subscriptionKeys.keyValues(caller.objectId(), name));
    }

    @Override
    @LogAudit(action = AuditAction.CONSUMER_SUBSCRIPTION_KEY_DELETED, target = "#name")
    public void deleteSubscriptionKey(final String name) {
        log.info("Delete APIM subscription key {}", name);
        subscriptionKeys.delete(caller.objectId(), name);
        applications.forgetApi(name);
    }
}
