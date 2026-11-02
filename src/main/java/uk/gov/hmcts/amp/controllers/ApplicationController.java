package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.ApiAccess;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.ApplicationsApi;
import uk.gov.hmcts.amp.openapi.model.ApiKey;
import uk.gov.hmcts.amp.openapi.model.Application;
import uk.gov.hmcts.amp.openapi.model.CreatedApplication;
import uk.gov.hmcts.amp.services.ApplicationService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ApplicationController implements ApplicationsApi {

    private final ApplicationService applications;
    private final ResponseMapper mapper;

    @Override
    public List<Application> listApplications(final String requestingUserId) {
        log.info("List applications");
        return mapper.toApplications(applications.list(requestingUserId));
    }

    @Override
    public Application getApplication(final String requestingUserId, final String clientId) {
        log.info("Read application {}", clientId);
        return mapper.toApplication(applications.get(requestingUserId, clientId));
    }

    @Override
    public CreatedApplication createApplication(final String requestingUserId, final ApplicationRequest request) {
        log.info("Register application '{}'", request.name());
        return mapper.toCreatedApplication(applications.create(requestingUserId, request));
    }

    @Override
    public void grantRole(final String requestingUserId, final String clientId, final ApiAccess request) {
        log.info("Grant access to {} for application {}", request.shortCode(), clientId);
        applications.grantAccess(requestingUserId, clientId, request.shortCode());
    }

    @Override
    public ApiKey getSubscriptionKey(final String requestingUserId, final String clientId, final String api) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not built yet.");
    }

    @Override
    public void deleteApplication(final String requestingUserId, final String clientId) {
        log.info("Delete application {}", clientId);
        applications.delete(requestingUserId, clientId);
    }
}
