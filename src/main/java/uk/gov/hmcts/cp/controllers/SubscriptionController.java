package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.SubscriptionService;
import uk.gov.hmcts.cp.services.UserService;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserService userService;

    @GetMapping("/subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> getAll() {
        log.info("Get all subscriptions request");
        return ResponseEntity.ok(subscriptionService.getAll());
    }

    @DeleteMapping("/subscriptions/{reference}")
    public ResponseEntity<Void> delete(@PathVariable final String reference) {
        log.info("Delete subscription request {}", reference);
        subscriptionService.delete(reference);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionResponse> submit(
        @RequestHeader("requestingUserId") final int requestingUserId,
        @Valid @RequestBody final SubscriptionRequest request) {
        log.info("Subscription request for user {}", requestingUserId);
        UserResponse userResponse = userService.validateUser(requestingUserId);
        SubscriptionResponse response = subscriptionService.submit(userResponse.getId(), request);
        return ResponseEntity.status(CREATED).body(response);
    }
}
