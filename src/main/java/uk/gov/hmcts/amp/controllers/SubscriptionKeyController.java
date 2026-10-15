package uk.gov.hmcts.amp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.NewSubscriptionKey;
import uk.gov.hmcts.amp.domain.SubscriptionKey;
import uk.gov.hmcts.amp.services.ApimSubscriptionKeyService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/apim/subscription-keys")
public class SubscriptionKeyController {

    private static final String REQUESTING_USER_ID = "requestingUserId";

    private final ApimSubscriptionKeyService subscriptionKeys;

    @GetMapping
    public List<SubscriptionKey> listKeys(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId) {
        log.info("List APIM subscription keys");
        return subscriptionKeys.list(requestingUserId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionKey createKey(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                     @Valid @RequestBody final NewSubscriptionKey request) {
        log.info("Create APIM subscription key {}", request.name());
        return subscriptionKeys.create(requestingUserId, request.name(), request.productId(),
            request.displayName());
    }

    @GetMapping("/{name}/values")
    public SubscriptionKey getKeyValues(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                        @PathVariable final String name) {
        log.info("Read APIM subscription key values for {}", name);
        return subscriptionKeys.keyValues(requestingUserId, name);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKey(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                          @PathVariable final String name) {
        log.info("Delete APIM subscription key {}", name);
        subscriptionKeys.delete(requestingUserId, name);
    }
}
