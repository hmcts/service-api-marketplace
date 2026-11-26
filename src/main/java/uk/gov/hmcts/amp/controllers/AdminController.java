package uk.gov.hmcts.amp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.audit.AuditAction;
import uk.gov.hmcts.amp.audit.LogAudit;
import uk.gov.hmcts.amp.mappers.ResponseMapper;
import uk.gov.hmcts.amp.openapi.api.AdminApi;
import uk.gov.hmcts.amp.openapi.model.AdminApplicationDetail;
import uk.gov.hmcts.amp.openapi.model.AdminApplicationSummary;
import uk.gov.hmcts.amp.openapi.model.AdminUser;
import uk.gov.hmcts.amp.openapi.model.AdminUserDetail;
import uk.gov.hmcts.amp.services.AdminService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

    private final AdminService admin;
    private final ResponseMapper mapper;

    @Override
    @LogAudit(action = AuditAction.ADMIN_USERS_LISTED)
    public List<AdminUser> listAdminUsers() {
        log.info("List users for an admin");
        return mapper.toAdminUsers(admin.listUsers());
    }

    @Override
    @LogAudit(action = AuditAction.ADMIN_USER_VIEWED, target = "#userId")
    public AdminUserDetail getAdminUser(final Long userId) {
        log.info("Read user {} for an admin", userId);
        return mapper.toAdminUserDetail(admin.getUser(userId));
    }

    @Override
    @LogAudit(action = AuditAction.ADMIN_APPLICATIONS_LISTED)
    public List<AdminApplicationSummary> listAdminApplications() {
        log.info("List applications for an admin");
        return mapper.toAdminApplicationSummaries(admin.listApplications());
    }

    @Override
    @LogAudit(action = AuditAction.ADMIN_APPLICATION_VIEWED, target = "#applicationId")
    public AdminApplicationDetail getAdminApplication(final Long applicationId) {
        log.info("Read application {} for an admin", applicationId);
        return mapper.toAdminApplicationDetail(admin.getApplication(applicationId));
    }
}
