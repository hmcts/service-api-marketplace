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
import uk.gov.hmcts.amp.domain.ApiAccess;
import uk.gov.hmcts.amp.domain.ApplicationResponse;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.services.ApplicationService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/applications")
public class ApplicationController {

    private static final String REQUESTING_USER_ID = "requestingUserId";

    private final ApplicationService applications;

    @GetMapping
    public List<ApplicationResponse> listApplications(
        @RequestHeader(REQUESTING_USER_ID) final String requestingUserId) {
        log.info("List applications");
        return applications.list(requestingUserId);
    }

    @GetMapping("/{clientId}")
    public ApplicationResponse getApplication(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                              @PathVariable final String clientId) {
        log.info("Read application {}", clientId);
        return applications.get(requestingUserId, clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createApplication(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                                 @Valid @RequestBody final ApplicationRequest request) {
        log.info("Register application '{}'", request.name());
        return applications.create(requestingUserId, request);
    }

    @PostMapping("/{clientId}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantAccess(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                            @PathVariable final String clientId,
                            @Valid @RequestBody final ApiAccess request) {
        log.info("Grant access to {} for application {}", request.shortCode(), clientId);
        applications.grantAccess(requestingUserId, clientId, request.shortCode());
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApplication(@RequestHeader(REQUESTING_USER_ID) final String requestingUserId,
                                  @PathVariable final String clientId) {
        log.info("Delete application {}", clientId);
        applications.delete(requestingUserId, clientId);
    }
}
