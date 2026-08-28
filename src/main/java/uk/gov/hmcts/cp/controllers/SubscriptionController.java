package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.services.SubscriptionService;
import uk.gov.hmcts.cp.services.UserService;

import static org.springframework.http.HttpStatus.CREATED;

@Slf4j
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> submit(
        @RequestHeader("requestingUserId") final int requestingUserId,
        @Valid @RequestBody final SubscriptionRequest request) {
        log.info("Subscription request for user {}", requestingUserId);
        UserEntity user = userService.validateUser(requestingUserId);
        return ResponseEntity.status(CREATED).body(subscriptionService.submit(user, request));
    }
}
