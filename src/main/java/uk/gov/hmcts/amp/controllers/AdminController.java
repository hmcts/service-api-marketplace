package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.AdminApi;
import uk.gov.hmcts.amp.openapi.model.AdminUser;
import uk.gov.hmcts.amp.services.AdminService;

import java.util.List;

/** Read-only views for the Marketplace Team. AdminService refuses anyone who is not an admin. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

    private final AdminService admin;
    private final ResponseMapper mapper;

    @Override
    public List<AdminUser> listAdminUsers(final String requestingUserId) {
        log.info("List users for an admin");
        return mapper.toAdminUsers(admin.listUsers(requestingUserId));
    }
}