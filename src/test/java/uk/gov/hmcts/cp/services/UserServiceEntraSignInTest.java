package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.EntraSignInRequest;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.UserMapper;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceEntraSignInTest {

    private static final String OID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    private static final OrganisationEntity NOT_SPECIFIED =
        OrganisationEntity.builder().id(9).name(AccountService.DEFAULT_ORGANISATION).build();

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private final UserResponse mapped = UserResponse.builder().id(42).build();

    @BeforeEach
    void setUp() {
        when(userMapper.fromEntity(any())).thenReturn(mapped);
        when(userRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(userRepository.findByEntraObjectId(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(organisationRepository.findByNameIgnoreCase(AccountService.DEFAULT_ORGANISATION))
            .thenReturn(Optional.of(NOT_SPECIFIED));
    }

    private static EntraSignInRequest request(final String email) {
        return EntraSignInRequest.builder().email(email).firstName(" Jo ").lastName("Bloggs").build();
    }

    private static UserEntity account(final String email, final String oid, final String status) {
        return UserEntity.builder().id(7L).firstName("Colin").lastName("Greenwood").email(email)
            .organisation(OrganisationEntity.builder().id(1).name("Api Marketplace").build())
            .role("producer").status(status).entraObjectId(oid).build();
    }

    private UserEntity saved() {
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void new_person_should_be_created_as_an_active_consumer_in_the_default_organisation() {
        assertThat(userService.signInWithEntra(OID, request(" Jo@Example.com "))).isEqualTo(mapped);

        UserEntity created = saved();
        assertThat(created.getEmail()).isEqualTo("jo@example.com");
        assertThat(created.getEntraObjectId()).isEqualTo(OID);
        assertThat(created.getFirstName()).isEqualTo("Jo");
        assertThat(created.getLastName()).isEqualTo("Bloggs");
        assertThat(created.getOrganisation()).isEqualTo(NOT_SPECIFIED);
        assertThat(created.getRole()).isEqualTo("consumer");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getPasswordHash()).isNull();
    }

    @Test
    void missing_names_should_be_stored_as_empty_rather_than_null() {
        userService.signInWithEntra(OID, EntraSignInRequest.builder().email("jo@example.com").build());

        assertThat(saved().getFirstName()).isEmpty();
    }

    @Test
    void the_default_organisation_should_be_created_when_it_does_not_exist_yet() {
        when(organisationRepository.findByNameIgnoreCase(AccountService.DEFAULT_ORGANISATION))
            .thenReturn(Optional.empty());
        when(organisationRepository.save(any())).thenReturn(NOT_SPECIFIED);

        userService.signInWithEntra(OID, request("jo@example.com"));

        assertThat(saved().getOrganisation()).isEqualTo(NOT_SPECIFIED);
    }

    @Test
    void known_person_with_the_same_email_should_be_returned_without_saving() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(account("jo@example.com", OID, "ACTIVE")));

        assertThat(userService.signInWithEntra(OID, request("JO@example.com"))).isEqualTo(mapped);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void an_upper_case_oid_should_match_the_stored_lower_case_one() {
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(account("jo@example.com", OID, "ACTIVE")));

        userService.signInWithEntra(OID.toUpperCase(), request("jo@example.com"));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void known_person_with_a_new_email_should_have_only_the_email_updated() {
        when(userRepository.findByEntraObjectId(OID))
            .thenReturn(Optional.of(account("old@example.com", OID, "ACTIVE")));

        userService.signInWithEntra(OID, request("new@example.com"));

        UserEntity updated = saved();
        assertThat(updated.getId()).isEqualTo(7);
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getFirstName()).isEqualTo("Colin");
        assertThat(updated.getRole()).isEqualTo("producer");
    }

    @Test
    void new_email_that_belongs_to_another_account_should_be_refused_with_409() {
        when(userRepository.findByEntraObjectId(OID))
            .thenReturn(Optional.of(account("old@example.com", OID, "ACTIVE")));
        when(userRepository.findByEmail("taken@example.com"))
            .thenReturn(Optional.of(account("taken@example.com", null, "ACTIVE")));

        assertStatus(() -> userService.signInWithEntra(OID, request("taken@example.com")), HttpStatus.CONFLICT);
    }

    @Test
    void an_existing_account_with_the_same_email_should_be_linked_and_keep_its_details() {
        when(userRepository.findByEmail("colin@example.com"))
            .thenReturn(Optional.of(account("colin@example.com", null, "ACTIVE")));

        userService.signInWithEntra(OID, request("colin@example.com"));

        UserEntity linked = saved();
        assertThat(linked.getId()).isEqualTo(7);
        assertThat(linked.getEntraObjectId()).isEqualTo(OID);
        assertThat(linked.getFirstName()).isEqualTo("Colin");
        assertThat(linked.getOrganisation().getName()).isEqualTo("Api Marketplace");
    }

    @Test
    void an_account_already_linked_to_another_entra_user_should_be_refused_with_409() {
        when(userRepository.findByEmail("colin@example.com")).thenReturn(
            Optional.of(account("colin@example.com", "99999999-9999-9999-9999-999999999999", "ACTIVE")));

        assertStatus(() -> userService.signInWithEntra(OID, request("colin@example.com")), HttpStatus.CONFLICT);
    }

    @Test
    void an_account_that_is_not_active_should_be_refused_with_403() {
        when(userRepository.findByEmail("colin@example.com"))
            .thenReturn(Optional.of(account("colin@example.com", null, "SUSPENDED")));

        assertStatus(() -> userService.signInWithEntra(OID, request("colin@example.com")), HttpStatus.FORBIDDEN);
    }

    @Test
    void malformed_oid_should_be_refused_with_400() {
        assertStatus(() -> userService.signInWithEntra("not-a-guid", request("jo@example.com")),
            HttpStatus.BAD_REQUEST);
    }

    @Test
    void losing_a_race_for_the_same_email_or_oid_should_be_reported_as_409() {
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertStatus(() -> userService.signInWithEntra(OID, request("jo@example.com")), HttpStatus.CONFLICT);
    }

    private static void assertStatus(final Runnable call, final HttpStatus status) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(status);
    }
}
