package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.amp.domain.ApiAccess;
import uk.gov.hmcts.amp.domain.ApplicationRequest;
import uk.gov.hmcts.amp.domain.ApplicationResponse;
import uk.gov.hmcts.amp.services.ApplicationService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String CLIENT_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private ApplicationService applications;

    @InjectMocks
    private ApplicationController controller;

    private final ApplicationResponse response = new ApplicationResponse(1L, CLIENT_ID, "Case Viewer", "case-viewer",
        "the", LocalDateTime.of(2028, 10, 15, 12, 0), LocalDateTime.of(2026, 10, 15, 12, 0), null);

    @Test
    void listing_should_return_what_the_service_found_for_that_user() {
        when(applications.list(OID)).thenReturn(List.of(response));

        assertThat(controller.listApplications(OID)).containsExactly(response);
    }

    @Test
    void reading_one_should_pass_the_user_and_the_client_id_through() {
        when(applications.get(OID, CLIENT_ID)).thenReturn(response);

        assertThat(controller.getApplication(OID, CLIENT_ID)).isEqualTo(response);
    }

    @Test
    void creating_should_pass_the_user_and_the_request_through() {
        ApplicationRequest request = new ApplicationRequest("Case Viewer", List.of());
        when(applications.create(OID, request)).thenReturn(response.withSecret("shown-once"));

        assertThat(controller.createApplication(OID, request).clientSecret()).isEqualTo("shown-once");
        verify(applications).create(OID, request);
    }

    @Test
    void granting_a_role_should_delegate_with_the_role_name() {
        controller.grantRole(OID, CLIENT_ID, new ApiAccess("app.read"));

        verify(applications).grantRole(OID, CLIENT_ID, "app.read");
    }

    @Test
    void deleting_should_delegate_by_user_and_client_id() {
        controller.deleteApplication(OID, CLIENT_ID);

        verify(applications).delete(OID, CLIENT_ID);
    }
}
