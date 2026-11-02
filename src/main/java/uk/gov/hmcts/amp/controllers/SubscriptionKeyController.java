package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.ApiSubscriptionRequest;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
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

    @Override
    public List<SubscriptionKey> listSubscriptionKeys(final String requestingUserId) {
        log.info("List APIM subscription keys");
        return mapper.toSubscriptionKeys(subscriptionKeys.list(requestingUserId));
    }

    @Override
    public ApiSubscription createSubscriptionKey(final String requestingUserId,
                                                         final ApiSubscriptionRequest request) {
        log.info("Subscribe application {} to {}", request.clientId(), request.shortCode());
        return mapper.toApiSubscription(
            applications.subscribe(requestingUserId, request.clientId(), request.shortCode()));
    }

    @Override
    public SubscriptionKey getSubscriptionKeyValues(final String requestingUserId, final String name) {
        log.info("Read APIM subscription key values for {}", name);
        return mapper.toSubscriptionKey(subscriptionKeys.keyValues(requestingUserId, name));
    }

    @Override
    public void deleteSubscriptionKey(final String requestingUserId, final String name) {
        log.info("Delete APIM subscription key {}", name);
        subscriptionKeys.delete(requestingUserId, name);
        applications.forgetApi(name);
    }
}
