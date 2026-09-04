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

import java.util.List;

import uk.gov.hmcts.cp.domain.PublishRequest;
import uk.gov.hmcts.cp.domain.PublishResponse;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.services.PublishService;
import uk.gov.hmcts.cp.services.UserService;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PublishController {

    private final PublishService publishService;
    private final UserService userService;

    @GetMapping("/publish-requests")
    public ResponseEntity<List<PublishResponse>> getAll() {
        log.info("Get all publish requests request");
        return ResponseEntity.ok(publishService.getAll());
    }

    @DeleteMapping("/publish-requests/{reference}")
    public ResponseEntity<Void> delete(@PathVariable final String reference) {
        log.info("Delete publish request {}", reference);
        publishService.delete(reference);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    @PostMapping("/publish-requests")
    public ResponseEntity<PublishResponse> submit(
        @RequestHeader("requestingUserId") final int requestingUserId,
        @Valid @RequestBody final PublishRequest request) {
        log.info("Publish request for user {}", requestingUserId);
        UserResponse userResponse = userService.validateUser(requestingUserId);
        PublishResponse response = publishService.submit(userResponse.getId(), request);
        return ResponseEntity.status(CREATED).body(response);
    }
}
