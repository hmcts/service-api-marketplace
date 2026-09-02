package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.domain.LoginRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class LoginIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private String body(final String email, final String password) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(
            LoginRequest.builder().email(email).password(password).build());
    }

    @Test
    void logging_in_with_a_seeded_email_should_return_200_with_that_user() throws Exception {
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("colin.greenwood@hmcts.net", "any-password")))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("colin.greenwood@hmcts.net"))
            .andExpect(jsonPath("$.firstName").value("Colin"));
    }

    @Test
    void logging_in_should_ignore_case_in_the_email() throws Exception {
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Colin.Greenwood@HMCTS.net", "any-password")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("colin.greenwood@hmcts.net"));
    }

    @Test
    void logging_in_with_an_unknown_email_should_return_404() throws Exception {
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("nobody@example.com", "any-password")))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    void logging_in_without_a_password_should_return_400() throws Exception {
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("colin.greenwood@hmcts.net", null)))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    void logging_in_with_a_malformed_email_should_return_400() throws Exception {
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("not-an-email", "any-password")))
            .andExpect(status().isBadRequest());
    }
}
