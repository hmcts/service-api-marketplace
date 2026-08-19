package uk.gov.hmcts.cp.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<String> welcome() {
        log.info("GET / called");
        return ok("Welcome to api-marketplace");
    }
}
