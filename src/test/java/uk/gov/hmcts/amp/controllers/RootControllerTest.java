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
    void the_root_should_return_a_message_starting_with_welcome() {
        assertThat(controller.getRoot()).startsWith("Welcome");
    }

    @Test
    void the_health_check_should_report_status_ok() {
        assertThat(controller.getHealth()).isEqualTo(new Health().status("ok"));
    }
}
