package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The account endpoints are stubbed - there is no user store, so these cover the response contract
// and the validation rules only, not registration conflicts or credential checking.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class AuthenticationIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registering_should_return_201_with_a_user_and_a_token_and_never_a_password() throws Exception {
        mockMvc.perform(register("register-flow@example.com"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.id").exists())
            .andExpect(jsonPath("$.user.email").exists())
            .andExpect(jsonPath("$.user.role").value("consumer"))
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.user.password").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void registering_with_a_short_password_should_return_400() throws Exception {
        mockMvc.perform(post("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Test","lastName":"Requester","email":"short-password@example.com",
                     "role":"consumer","password":"tooshort"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Password must be at least 12 characters long."));
    }

    @Test
    void registering_with_an_unknown_role_should_return_400() throws Exception {
        mockMvc.perform(post("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Test","lastName":"Requester","email":"bad-role@example.com",
                     "role":"administrator","password":"%s"}
                    """.formatted(PASSWORD)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void signing_in_should_return_200_with_a_token() throws Exception {
        mockMvc.perform(login("login-flow@example.com", PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.user.id").exists());
    }

    @Test
    void signing_in_without_a_password_should_return_400() throws Exception {
        mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"login-flow@example.com"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void requesting_the_current_user_with_a_bearer_token_should_return_an_account() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer any-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").exists())
            .andExpect(jsonPath("$.user.role").value("consumer"));
    }

    @Test
    void requesting_the_current_user_without_a_token_should_return_401() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Session expired. Please sign in again."));
    }

    @Test
    void signing_out_should_return_200_with_ok_true() throws Exception {
        mockMvc.perform(post("/api/logout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    private RequestBuilder register(final String email) {
        return post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"firstName":"Test","lastName":"Requester","email":"%s","organisation":"HMCTS",
                 "role":"consumer","password":"%s"}
                """.formatted(email, PASSWORD));
    }

    private RequestBuilder login(final String email, final String password) {
        return post("/api/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }
}
