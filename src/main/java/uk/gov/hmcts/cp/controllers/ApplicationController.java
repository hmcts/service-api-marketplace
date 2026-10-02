package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import uk.gov.hmcts.cp.domain.ApplicationRequest;
import uk.gov.hmcts.cp.domain.ApplicationResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.ApplicationService;
import uk.gov.hmcts.cp.services.UserService;

import static org.springframework.http.HttpStatus.CREATED;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final UserService userService;

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getForUser(
        @RequestHeader("requestingUserId") final int requestingUserId) {
        log.info("List applications for user {}", requestingUserId);
        UserResponse user = userService.validateUser(requestingUserId);
        return ResponseEntity.ok(applicationService.getForUser(user.getId()));
    }

    @PostMapping("/applications")
    public ResponseEntity<ApplicationResponse> register(
        @RequestHeader("requestingUserId") final int requestingUserId,
        @Valid @RequestBody final ApplicationRequest request) {
        log.info("Application registration for user {}", requestingUserId);
        UserResponse user = userService.validateUser(requestingUserId);
        ApplicationResponse response = applicationService.register(user.getId(), request);
        return ResponseEntity.status(CREATED).body(response);
    }
}
