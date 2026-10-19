package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionRequestServiceTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String CLIENT_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private ProductionRequestRepository requests;

    @Mock
    private ApplicationRepository applications;

    @Mock
    private UserService users;

    @Mock
    private ClockService clockService;

    @InjectMocks
    private ProductionRequestService service;

    @Test
    void references_that_keep_colliding_should_give_up_rather_than_loop() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));
        when(requests.existsByReference(any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(OID, form()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        verify(requests, never()).saveAndFlush(any());
    }

    private UserEntity signedIn() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("ada.lovelace@hmcts.net");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        return user;
    }

    private ApplicationEntity owned(final UserEntity user) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(10L);
        application.setUser(user);
        application.setClientId(CLIENT_ID);
        application.setName("Case Viewer");
        return application;
    }

    private ProductionAccessRequest form() {
        return new ProductionAccessRequest(CLIENT_ID, "Example Legal Ltd", LocalDate.of(2027, 3, 27),
            "low", "yes", "in-place", "security@example.com", "Checking hearing schedules.", true);
    }
}
