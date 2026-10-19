package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.domain.ProductionAccessDetail;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Test
    void reading_a_request_should_return_what_was_asked_for() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(requests.findByUserIdAndReference(user.getId(), "PCR-ABCD2345"))
            .thenReturn(Optional.of(stored(user)));

        ProductionAccessDetail detail = service.get(OID, "PCR-ABCD2345");

        assertThat(detail.reference()).isEqualTo("PCR-ABCD2345");
        assertThat(detail.organisation()).isEqualTo("Example Legal Ltd");
        assertThat(detail.useCase()).isEqualTo("Checking hearing schedules.");
        assertThat(detail.applicationName()).isEqualTo("Case Viewer");
        assertThat(detail.clientId()).isEqualTo(CLIENT_ID);
        assertThat(detail.declarationConfirmed()).isTrue();
    }

    @Test
    void another_users_reference_should_be_not_found_rather_than_refused() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        // The lookup is keyed on the owner too, so someone else's reference simply is not there
        // and the caller cannot tell it apart from one that never existed.
        when(requests.findByUserIdAndReference(user.getId(), "PCR-SOMEONE1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OID, "PCR-SOMEONE1"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleted_application_should_leave_the_request_readable() {
        UserEntity user = signedIn();
        ProductionRequestEntity request = stored(user);
        request.setApplication(null);
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(requests.findByUserIdAndReference(user.getId(), "PCR-ABCD2345")).thenReturn(Optional.of(request));

        ProductionAccessDetail detail = service.get(OID, "PCR-ABCD2345");

        assertThat(detail.clientId()).isNull();
        assertThat(detail.applicationName()).isEqualTo("Case Viewer");
    }

    private ProductionRequestEntity stored(final UserEntity user) {
        ProductionRequestEntity request = new ProductionRequestEntity();
        request.setUser(user);
        request.setApplication(owned(user));
        request.setApplicationName("Case Viewer");
        request.setReference("PCR-ABCD2345");
        request.setStatus(ProductionRequestEntity.SUBMITTED);
        request.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 11, 0));
        request.setOrganisation("Example Legal Ltd");
        request.setGoLiveDate(LocalDate.of(2027, 3, 27));
        request.setCallVolume("low");
        request.setDpia("yes");
        request.setDsa("in-place");
        request.setSecurityContact("security@example.com");
        request.setUseCase("Checking hearing schedules.");
        request.setDeclarationConfirmed(true);
        return request;
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
