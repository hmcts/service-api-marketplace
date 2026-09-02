package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.domain.HealthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

@ExtendWith(MockitoExtension.class)
class RootControllerTest {

    @InjectMocks
    private RootController controller;

    @Test
    void calling_welcome_should_return_200_with_welcome_message() {
        ResponseEntity<String> response = controller.welcome();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).startsWith("Welcome");
    }

    @Test
    void calling_health_should_return_200_with_status_ok() {
        ResponseEntity<HealthResponse> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().status()).isEqualTo("ok");
    }
}
