package uk.gov.hmcts.cp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTest {

    void main_should_run() {
        Application.main(new String[]{});
    }
}
