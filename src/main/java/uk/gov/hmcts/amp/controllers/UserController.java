package uk.gov.hmcts.amp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.domain.EntraUserRequest;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.services.UserService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService users;

    @PutMapping("/entra/{entraObjectId}")
    public UserResponse upsertEntraUser(@PathVariable final String entraObjectId,
                                        @Valid @RequestBody final EntraUserRequest request) {
        log.info("Upsert the user Entra has signed in");
        return users.upsertEntraUser(entraObjectId, request);
    }
}
