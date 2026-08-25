package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestContainersInitialise.class)
class RequestsIntegrationTest {

    private static final String ACCESS_REQUEST_BODY = """
        {"fullName":"Test Requester","organisation":"HMCTS","email":"requester@example.com",
         "jobTitle":"Software Engineer","apiName":"RAG Service API","environment":"sandbox",
         "callVolume":"1000/day","useCase":"Automating hearing result document distribution"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void posting_an_access_request_should_return_201_with_a_reference() throws Exception {
        mockMvc.perform(post("/api/requests/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ACCESS_REQUEST_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reference").value(matchesPattern("^AR-[0-9]{4}-[A-Z0-9]{6}$")));
    }

    @Test
    void posting_an_access_request_should_echo_a_correlation_id_header() throws Exception {
        mockMvc.perform(post("/api/requests/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ACCESS_REQUEST_BODY))
            .andExpect(header().string("X-Correlation-Id", notNullValue()));
    }

    @Test
    void posting_an_access_request_with_an_invalid_email_should_return_400() throws Exception {
        mockMvc.perform(post("/api/requests/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Test Requester","organisation":"HMCTS","email":"not-an-email",
                     "apiName":"RAG Service API","environment":"sandbox","useCase":"Testing"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Enter a valid email address."))
            .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    void posting_an_onboarding_request_should_return_201() throws Exception {
        mockMvc.perform(post("/v1/requests/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Test Requester","organisation":"HMCTS","email":"requester@example.com",
                     "jobTitle":"Software Engineer","apiRequested":"RAG Service API",
                     "environment":"sandbox","useCase":"Testing"}
                    """))
            .andExpect(status().isCreated());
    }
}
