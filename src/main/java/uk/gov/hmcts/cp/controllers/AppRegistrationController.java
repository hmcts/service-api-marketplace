package uk.gov.hmcts.cp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.AppRegistration;
import uk.gov.hmcts.cp.domain.NewAppRegistration;
import uk.gov.hmcts.cp.services.EntraAppRegistrationService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/applications-new")
public class AppRegistrationController {

    private final EntraAppRegistrationService registrations;

    @GetMapping
    public List<AppRegistration> listApplications() {
        log.info("List Entra application registrations");
        return registrations.list();
    }

    @GetMapping("/{clientId}")
    public AppRegistration getApplication(@PathVariable final String clientId) {
        log.info("Read Entra application registration {}", clientId);
        return registrations.get(clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppRegistration createApplication(@Valid @RequestBody final NewAppRegistration request) {
        log.info("Register Entra application '{}' with roles {}", request.name(), request.roles());
        return registrations.register(request.name(), request.roles());
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApplication(@PathVariable final String clientId) {
        log.info("Delete Entra application registration {}", clientId);
        registrations.delete(clientId);
    }
}
