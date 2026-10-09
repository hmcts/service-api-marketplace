package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.NewSubscriptionKey;
import uk.gov.hmcts.cp.domain.SubscriptionKey;
import uk.gov.hmcts.cp.services.ApimSubscriptionKeyService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/apim/subscription-keys")
public class SubscriptionKeyController {

    private final ApimSubscriptionKeyService subscriptionKeys;

    @GetMapping
    public List<SubscriptionKey> listKeys() {
        log.info("List APIM subscription keys");
        return subscriptionKeys.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionKey createKey(@Valid @RequestBody final NewSubscriptionKey request) {
        log.info("Create APIM subscription key {}", request.name());
        return subscriptionKeys.create(request.name(), request.productId(), request.displayName());
    }

    @GetMapping("/{name}/values")
    public SubscriptionKey getKeyValues(@PathVariable final String name) {
        log.info("Read APIM subscription key values for {}", name);
        return subscriptionKeys.keyValues(name);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKey(@PathVariable final String name) {
        log.info("Delete APIM subscription key {}", name);
        subscriptionKeys.delete(name);
    }
}
