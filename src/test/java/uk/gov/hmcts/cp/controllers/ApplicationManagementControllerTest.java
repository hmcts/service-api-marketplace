package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AddTeamMemberRequest;
import uk.gov.hmcts.cp.domain.ApiKeySummary;
import uk.gov.hmcts.cp.domain.ApiSubscription;
import uk.gov.hmcts.cp.domain.ApplicationDetailResponse;
import uk.gov.hmcts.cp.domain.ApplicationEnvelope;
import uk.gov.hmcts.cp.domain.ApplicationListResponse;
import uk.gov.hmcts.cp.domain.ApplicationView;
import uk.gov.hmcts.cp.domain.ConnectApiRequest;
import uk.gov.hmcts.cp.domain.ConnectedApi;
import uk.gov.hmcts.cp.domain.CreateApplicationRequest;
import uk.gov.hmcts.cp.domain.CreatedApplicationResponse;
import uk.gov.hmcts.cp.domain.NewApiKeyResponse;
import uk.gov.hmcts.cp.domain.OkResponse;
import uk.gov.hmcts.cp.domain.OwnerRef;
import uk.gov.hmcts.cp.domain.TeamMemberEnvelope;
import uk.gov.hmcts.cp.domain.TeamMemberView;
import uk.gov.hmcts.cp.domain.TeamMembersResponse;
import uk.gov.hmcts.cp.domain.UpdateApplicationRequest;
import uk.gov.hmcts.cp.exceptions.GlobalExceptionHandler;
import uk.gov.hmcts.cp.services.ApplicationManagementService;
import uk.gov.hmcts.cp.services.ClockService;
import uk.gov.hmcts.cp.services.TeamMemberService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApplicationManagementControllerTest {

    private static final String APP = "3f8a1c94-6b2e-4d51-9a77-0e1c5b8d4f23";
    private static final String BASE = "/api/applications";
    private static final String AUTH = "Bearer the-token";
    private static final ApplicationView VIEW = new ApplicationView(APP, APP, "Alpha", "first", "sandbox",
        new OwnerRef("user", 7), null, null, Map.of("team", "alpha"), List.of(new ConnectedApi("api-1", "One")),
        "2026-10-02T10:00:00Z", "owner");

    @Mock
    private ApplicationManagementService applications;

    @Mock
    private TeamMemberService teamMembers;

    @Mock
    private ClockService clockService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lenient().when(clockService.now()).thenReturn(Instant.parse("2026-10-02T10:00:00Z"));
        mvc = MockMvcBuilders
            .standaloneSetup(new ApplicationManagementController(applications, teamMembers))
            .setControllerAdvice(new GlobalExceptionHandler(clockService))
            .build();
    }

    @Test
    void listing_should_return_the_applications_wrapped_as_the_frontend_reads_them() throws Exception {
        when(applications.list(AUTH)).thenReturn(new ApplicationListResponse(List.of(VIEW)));

        mvc.perform(get(BASE).header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applications[0].id").value(APP))
            .andExpect(jsonPath("$.applications[0].name").value("Alpha"))
            .andExpect(jsonPath("$.applications[0].clientId").value(APP))
            .andExpect(jsonPath("$.applications[0].viewerRole").value("owner"))
            .andExpect(jsonPath("$.applications[0].owner.type").value("user"))
            .andExpect(jsonPath("$.applications[0].owner.id").value(7))
            .andExpect(jsonPath("$.applications[0].customAttributes.team").value("alpha"))
            .andExpect(jsonPath("$.applications[0].connectedApis[0].id").value("api-1"))
            .andExpect(jsonPath("$.applications[0].createdAt").value("2026-10-02T10:00:00Z"));
    }

    @Test
    void call_with_no_token_should_reach_the_service_as_null_so_it_can_answer_401() throws Exception {
        when(applications.list(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in."));

        mvc.perform(get(BASE))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Not signed in."));
    }

    @Test
    void creating_should_return_201_with_the_application_and_the_one_time_secret() throws Exception {
        when(applications.create(any(), any(CreateApplicationRequest.class)))
            .thenReturn(new CreatedApplicationResponse(VIEW, "amp_secret"));

        mvc.perform(post(BASE).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alpha\",\"environment\":\"sandbox\",\"description\":\"first\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.apiKey").value("amp_secret"))
            .andExpect(jsonPath("$.application.name").value("Alpha"));

        ArgumentCaptor<CreateApplicationRequest> sent = ArgumentCaptor.forClass(CreateApplicationRequest.class);
        verify(applications).create(eq(AUTH), sent.capture());
        assertThat(sent.getValue().getName()).isEqualTo("Alpha");
        assertThat(sent.getValue().getEnvironment()).isEqualTo("sandbox");
        assertThat(sent.getValue().getDescription()).isEqualTo("first");
    }

    @Test
    void refusal_should_come_back_with_its_status_and_the_message_in_error() throws Exception {
        when(applications.create(any(), any(CreateApplicationRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "You already have an application."));

        mvc.perform(post(BASE).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alpha\",\"environment\":\"sandbox\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("You already have an application."));
    }

    @Test
    void detail_should_return_the_application_and_its_secrets_by_preview() throws Exception {
        when(applications.detail(AUTH, APP)).thenReturn(new ApplicationDetailResponse(VIEW,
            List.of(new ApiKeySummary("key-1", "a1b2", "2026-10-02T10:00:00Z", null)),
            List.of(new ApiSubscription("hearing-results", "sub-key-1"))));

        mvc.perform(get(BASE + "/" + APP).header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application.id").value(APP))
            .andExpect(jsonPath("$.apiKeys[0].preview").value("a1b2"))
            .andExpect(jsonPath("$.apiKeys[0].revokedAt").doesNotExist())
            .andExpect(jsonPath("$.apiSubscriptions[0].apiId").value("hearing-results"))
            .andExpect(jsonPath("$.apiSubscriptions[0].subscriptionKey").value("sub-key-1"));
    }

    @Test
    void editing_should_answer_to_patch_which_is_what_the_frontend_sends() throws Exception {
        when(applications.update(any(), any(), any(UpdateApplicationRequest.class)))
            .thenReturn(new ApplicationEnvelope(VIEW));

        mvc.perform(patch(BASE + "/" + APP).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"changed\",\"customAttributes\":{\"tier\":\"gold\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application.name").value("Alpha"));

        ArgumentCaptor<UpdateApplicationRequest> sent = ArgumentCaptor.forClass(UpdateApplicationRequest.class);
        verify(applications).update(eq(AUTH), eq(APP),
            sent.capture());
        assertThat(sent.getValue().getDescription()).isEqualTo("changed");
        assertThat(sent.getValue().getCustomAttributes()).containsEntry("tier", "gold");
        assertThat(sent.getValue().getCallbackUrl()).isNull();
    }

    @Test
    void editing_should_also_answer_to_put_as_a_way_round_a_patch_that_gets_no_reply() throws Exception {
        when(applications.update(any(), any(), any(UpdateApplicationRequest.class)))
            .thenReturn(new ApplicationEnvelope(VIEW));

        mvc.perform(put(BASE + "/" + APP).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"changed\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application.name").value("Alpha"));
    }

    @Test
    void deleting_should_return_204_with_no_body() throws Exception {
        mvc.perform(delete(BASE + "/" + APP).header("Authorization", AUTH))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(applications).delete(AUTH, APP);
    }

    @Test
    void making_a_secret_should_return_201_with_its_id_and_the_one_time_value() throws Exception {
        when(applications.newSecret(AUTH, APP)).thenReturn(new NewApiKeyResponse("key-2", "amp_new"));

        mvc.perform(post(BASE + "/" + APP + "/api-keys").header("Authorization", AUTH))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("key-2"))
            .andExpect(jsonPath("$.apiKey").value("amp_new"));
    }

    @Test
    void revoking_a_secret_should_return_ok_true() throws Exception {
        when(applications.revokeSecret(AUTH, APP, "key-1")).thenReturn(new OkResponse(true));

        mvc.perform(delete(BASE + "/" + APP + "/api-keys/key-1").header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"ok\":true}"));
    }

    @Test
    void connecting_an_api_should_return_201_with_the_updated_application() throws Exception {
        when(applications.connectApi(any(), any(), any(ConnectApiRequest.class)))
            .thenReturn(new ApplicationEnvelope(VIEW));

        mvc.perform(post(BASE + "/" + APP + "/connected-apis").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"api-1\",\"name\":\"One\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.application.connectedApis[0].name").value("One"));

        ArgumentCaptor<ConnectApiRequest> sent = ArgumentCaptor.forClass(ConnectApiRequest.class);
        verify(applications).connectApi(eq(AUTH), eq(APP),
            sent.capture());
        assertThat(sent.getValue().getId()).isEqualTo("api-1");
        assertThat(sent.getValue().getName()).isEqualTo("One");
    }

    @Test
    void disconnecting_an_api_should_return_the_updated_application() throws Exception {
        when(applications.disconnectApi(AUTH, APP, "api-1")).thenReturn(new ApplicationEnvelope(VIEW));

        mvc.perform(delete(BASE + "/" + APP + "/connected-apis/api-1").header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application.id").value(APP));
    }

    @Test
    void the_team_should_list_with_the_owners_email() throws Exception {
        when(teamMembers.list(AUTH, APP)).thenReturn(new TeamMembersResponse("olive@example.com",
            List.of(new TeamMemberView("m-1", "dan@example.com", "developer", "2026-10-02T10:00:00Z"))));

        mvc.perform(get(BASE + "/" + APP + "/team-members").header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerEmail").value("olive@example.com"))
            .andExpect(jsonPath("$.teamMembers[0].email").value("dan@example.com"))
            .andExpect(jsonPath("$.teamMembers[0].role").value("developer"));
    }

    @Test
    void adding_a_team_member_should_return_201_with_the_member() throws Exception {
        when(teamMembers.add(any(), any(), any(AddTeamMemberRequest.class))).thenReturn(
            new TeamMemberEnvelope(new TeamMemberView("m-1", "dan@example.com", "developer", "2026-10-02T10:00:00Z")));

        mvc.perform(post(BASE + "/" + APP + "/team-members").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dan@example.com\",\"role\":\"developer\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.teamMember.id").value("m-1"))
            .andExpect(jsonPath("$.teamMember.email").value("dan@example.com"));

        ArgumentCaptor<AddTeamMemberRequest> sent = ArgumentCaptor.forClass(AddTeamMemberRequest.class);
        verify(teamMembers).add(eq(AUTH), eq(APP),
            sent.capture());
        assertThat(sent.getValue().getEmail()).isEqualTo("dan@example.com");
        assertThat(sent.getValue().getRole()).isEqualTo("developer");
    }

    @Test
    void removing_a_team_member_should_return_ok_true() throws Exception {
        when(teamMembers.remove(AUTH, APP, "m-1")).thenReturn(new OkResponse(true));

        mvc.perform(delete(BASE + "/" + APP + "/team-members/m-1").header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"ok\":true}"));
    }

    @Test
    void body_that_is_not_json_should_be_a_400_not_a_500() throws Exception {
        mvc.perform(post(BASE).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content("nope"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("The request could not be read."));
    }

    @Test
    void an_unsupported_method_should_be_a_405_with_the_standard_error_body() throws Exception {
        mvc.perform(put(BASE).header("Authorization", AUTH))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.error").value("Method not allowed."));
    }
}
