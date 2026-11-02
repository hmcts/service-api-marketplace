package uk.gov.hmcts.amp.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.openapi.api.RootApi;
import uk.gov.hmcts.amp.openapi.model.Health;

@Slf4j
@RestController
public class RootController implements RootApi {

    @Override
    public String getRoot() {
        log.info("GET / called");
        return "Welcome to api-marketplace";
    }

    @Override
    public Health getHealth() {
        return new Health().status("ok");
    }
}
