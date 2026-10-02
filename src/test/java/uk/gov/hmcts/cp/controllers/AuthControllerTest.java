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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AccountResponse;
import uk.gov.hmcts.cp.domain.AuthResponse;
import uk.gov.hmcts.cp.domain.LoginRequest;
import uk.gov.hmcts.cp.domain.MeResponse;
import uk.gov.hmcts.cp.domain.RegisterRequest;
import uk.gov.hmcts.cp.exceptions.GlobalExceptionHandler;
import uk.gov.hmcts.cp.services.AccountService;
import uk.gov.hmcts.cp.services.AuthRateLimiter;
import uk.gov.hmcts.cp.services.ClockService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc with the real GlobalExceptionHandler, so these check what the frontend will
 * actually receive - the status code and the {"error": "..."} body it reads - not just what the
 * controller method returns.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final AccountResponse JOE = new AccountResponse(11, "Joe", "Bloggs", "joe@example.com", "consumer");

    @Mock
    private AccountService accountService;

    @Mock
    private AuthRateLimiter rateLimiter;

    @Mock
    private ClockService clockService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lenient().when(clockService.now()).thenReturn(Instant.parse("2026-10-02T10:00:00Z"));
        lenient().when(rateLimiter.tryAcquire(any())).thenReturn(true);
        mvc = MockMvcBuilders
            .standaloneSetup(new AuthController(accountService, rateLimiter))
            .setControllerAdvice(new GlobalExceptionHandler(clockService))
            .build();
    }

    private static RequestPostProcessor clientAt(final String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static final String REGISTER_BODY = """
        {"firstName":"Joe","lastName":"Bloggs","email":"joe@example.com",
         "organisation":"HMCTS","role":"consumer","password":"correct-horse-battery-staple"}""";

    @Test
    void registering_should_return_201_with_the_user_and_a_token_in_the_shape_the_frontend_reads() throws Exception {
        when(accountService.register(any(RegisterRequest.class))).thenReturn(new AuthResponse(JOE, "the-token"));

        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value("the-token"))
            .andExpect(jsonPath("$.user.id").value(11))
            .andExpect(jsonPath("$.user.firstName").value("Joe"))
            .andExpect(jsonPath("$.user.lastName").value("Bloggs"))
            .andExpect(jsonPath("$.user.email").value("joe@example.com"))
            .andExpect(jsonPath("$.user.role").value("consumer"))
            .andExpect(jsonPath("$.user.password").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void the_request_body_should_reach_the_service_field_for_field() throws Exception {
        when(accountService.register(any(RegisterRequest.class))).thenReturn(new AuthResponse(JOE, "t"));

        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY));

        ArgumentCaptor<RegisterRequest> sent = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(accountService).register(sent.capture());
        assertThat(sent.getValue().getFirstName()).isEqualTo("Joe");
        assertThat(sent.getValue().getLastName()).isEqualTo("Bloggs");
        assertThat(sent.getValue().getEmail()).isEqualTo("joe@example.com");
        assertThat(sent.getValue().getOrganisation()).isEqualTo("HMCTS");
        assertThat(sent.getValue().getRole()).isEqualTo("consumer");
        assertThat(sent.getValue().getPassword()).isEqualTo("correct-horse-battery-staple");
    }

    @Test
    void an_empty_body_should_reach_the_service_so_it_can_say_which_fields_are_missing() throws Exception {
        when(accountService.register(any(RegisterRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields."));

        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Missing required fields."));
    }

    @Test
    void errors_should_come_back_as_an_error_field_holding_the_message() throws Exception {
        when(accountService.register(any(RegisterRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "An account with these details could not be created."));

        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("An account with these details could not be created."));
    }

    @Test
    void body_that_is_not_json_should_be_a_400_not_a_500() throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("The request could not be read."));

        verifyNoInteractions(accountService);
    }

    @Test
    void signing_in_should_return_200_with_the_user_and_a_token() throws Exception {
        when(accountService.login(any(LoginRequest.class))).thenReturn(new AuthResponse(JOE, "the-token"));

        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"correct-horse-battery-staple\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("the-token"))
            .andExpect(jsonPath("$.user.email").value("joe@example.com"));
    }

    @Test
    void failed_sign_in_should_be_a_401_with_the_message_the_frontend_shows() throws Exception {
        when(accountService.login(any(LoginRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"wrong-password-1\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Incorrect email or password."));
    }

    @Test
    void sign_in_being_unavailable_should_be_a_503_with_a_message_that_says_nothing_about_why() throws Exception {
        when(accountService.login(any(LoginRequest.class))).thenThrow(new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "Sign-in is not available right now. Please try again later."));

        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joe@example.com\",\"password\":\"correct-horse-battery-staple\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Sign-in is not available right now. Please try again later."));
    }

    @Test
    void signing_out_should_acknowledge_without_needing_a_token() throws Exception {
        mvc.perform(post("/api/logout"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"ok\":true}"));

        verifyNoInteractions(accountService);
    }

    @Test
    void asking_who_i_am_should_pass_the_authorization_header_through() throws Exception {
        when(accountService.currentUser("Bearer the-token")).thenReturn(new MeResponse(JOE));

        mvc.perform(get("/api/me").header("Authorization", "Bearer the-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value(11))
            .andExpect(jsonPath("$.user.email").value("joe@example.com"));
    }

    @Test
    void asking_who_i_am_with_no_header_should_reach_the_service_and_be_a_401_not_a_400() throws Exception {
        when(accountService.currentUser(null))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in."));

        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Not signed in."));
    }

    @Test
    void too_many_attempts_from_one_address_should_be_a_429_and_never_reach_the_service() throws Exception {
        when(rateLimiter.tryAcquire("203.0.113.9")).thenReturn(false);

        mvc.perform(post("/api/login")
                .with(clientAt("203.0.113.9"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"x\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("Too many attempts. Please try again later."));

        verifyNoInteractions(accountService);
    }

    @Test
    void sign_in_and_registration_should_share_one_allowance_keyed_on_the_client_address() throws Exception {
        when(accountService.login(any(LoginRequest.class))).thenReturn(new AuthResponse(JOE, "t"));
        when(accountService.register(any(RegisterRequest.class))).thenReturn(new AuthResponse(JOE, "t"));

        mvc.perform(post("/api/login")
                .with(clientAt("198.51.100.4"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"x\"}"));
        mvc.perform(post("/api/register")
                .with(clientAt("198.51.100.4"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY));

        verify(rateLimiter, times(2)).tryAcquire("198.51.100.4");
    }

    @Test
    void me_and_logout_should_not_count_towards_the_sign_in_allowance() throws Exception {
        when(accountService.currentUser("Bearer t")).thenReturn(new MeResponse(JOE));

        mvc.perform(get("/api/me").header("Authorization", "Bearer t"));
        mvc.perform(post("/api/logout"));

        verify(rateLimiter, never()).tryAcquire(any());
    }
}
