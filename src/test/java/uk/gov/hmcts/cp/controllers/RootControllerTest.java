package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

class RootControllerTest {

    private final RootController controller = new RootController();

    @Test
    void calling_welcome_should_return_200_with_welcome_message() {
        ResponseEntity<String> response = controller.welcome();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).startsWith("Welcome");
    }
}
