package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.amp.openapi.model.Health;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RootControllerTest {

    @InjectMocks
    private RootController controller;

    @Test
    void calling_welcome_should_return_the_welcome_message() {
        assertThat(controller.getRoot()).startsWith("Welcome");
    }

    @Test
    void calling_health_should_return_status_ok() {
        assertThat(controller.getHealth()).isEqualTo(new Health().status("ok"));
    }
}
