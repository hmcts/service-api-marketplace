package uk.gov.hmcts.amp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(properties = "entra.onboarding-client-secret=a-test-secret")
@AutoConfigureMockMvc
class ApplicationTest {

    void main_should_run() {
        Application.main(new String[]{});
    }
}
