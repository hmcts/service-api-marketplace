package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import uk.gov.hmcts.cp.domain.RequestSummaryResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.RequestService;
import uk.gov.hmcts.cp.services.UserService;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;
    private final UserService userService;

    @GetMapping("/requests")
    public ResponseEntity<List<RequestSummaryResponse>> getForUser(
        @RequestHeader("requestingUserId") final int requestingUserId) {
        log.info("List requests for user {}", requestingUserId);
        UserResponse user = userService.validateUser(requestingUserId);
        return ResponseEntity.ok(requestService.getForUser(user.getEmail()));
    }
}
