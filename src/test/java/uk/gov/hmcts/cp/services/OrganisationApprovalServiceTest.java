package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.EntraIdentity;
import uk.gov.hmcts.cp.entity.OrganisationDomainEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.OrganisationDomainRepository;
import uk.gov.hmcts.cp.repository.UserRepository;
import uk.gov.hmcts.cp.services.OrganisationApprovalService.Caller;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganisationApprovalServiceTest {

    private static final String OID = "11111111-2222-3333-4444-555555555555";
    private static final OrganisationEntity ORG = OrganisationEntity.builder().id(7).name("Example Council").build();

    @Mock
    private EntraTokenVerifier verifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganisationDomainRepository domainRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private OrganisationApprovalService service;

    @BeforeEach
    void setUp() {
        service = new OrganisationApprovalService(verifier, userRepository, domainRepository, transactionTemplate);
        ReflectionTestUtils.setField(service, "mode", "on");
        ReflectionTestUtils.setField(service, "adminEmails", "Admin@Example.gov.uk, other@example.gov.uk");
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
            invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void signedInAs(final String email, final String name) {
        when(verifier.verify("Bearer t")).thenReturn(new EntraIdentity(OID, "tenant", email, name));
    }

    private void listed(final String domain) {
        when(domainRepository.findByDomainIgnoreCase(domain)).thenReturn(Optional.of(
            OrganisationDomainEntity.builder().domain(domain).organisation(ORG).build()));
    }

    private ResponseStatusException refusal(final Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run);
    }

    @Test
    void new_person_on_an_approved_domain_is_saved_active_in_that_organisation() {
        signedInAs("Dee.Mo@Example.gov.uk", "Dee Mo");
        listed("example.gov.uk");

        Caller caller = service.caller("Bearer t");

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEntraObjectId()).isEqualTo(OID);
        assertThat(saved.getValue().getEmail()).isEqualTo("dee.mo@example.gov.uk");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Dee");
        assertThat(saved.getValue().getLastName()).isEqualTo("Mo");
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getValue().getRole()).isEqualTo("consumer");
        assertThat(saved.getValue().getOrganisation()).isEqualTo(ORG);
        assertThat(saved.getValue().getPasswordHash()).isNull();
        assertThat(caller.user().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void person_on_a_domain_that_is_not_approved_is_refused_and_nothing_is_saved() {
        signedInAs("dee@unknown.example", "Dee");
        when(domainRepository.findByDomainIgnoreCase("unknown.example")).thenReturn(Optional.empty());

        ResponseStatusException refused = refusal(() -> service.caller("Bearer t"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.getReason()).startsWith("DOMAIN_NOT_APPROVED").contains("unknown.example");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void existing_person_is_refused_as_soon_as_their_domain_comes_off_the_list() {
        signedInAs("dee@example.gov.uk", "Dee Mo");
        when(domainRepository.findByDomainIgnoreCase("example.gov.uk")).thenReturn(Optional.empty());

        assertThat(refusal(() -> service.caller("Bearer t")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findByEntraObjectId(anyString());
    }

    @Test
    void existing_person_is_refreshed_from_the_token_but_not_saved_again_if_nothing_changed() {
        signedInAs("dee@example.gov.uk", "Dee Mo");
        listed("example.gov.uk");
        UserEntity same = UserEntity.builder().id(3).entraObjectId(OID).email("dee@example.gov.uk").firstName("Dee")
            .lastName("Mo").role("consumer").status("ACTIVE").organisation(ORG).build();
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(same));

        assertThat(service.caller("Bearer t").user()).isEqualTo(same);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void existing_person_whose_name_or_organisation_changed_is_updated() {
        signedInAs("dee@example.gov.uk", "Deirdre Mo");
        listed("example.gov.uk");
        UserEntity before = UserEntity.builder().id(3).entraObjectId(OID).email("dee@example.gov.uk")
            .firstName("Dee").lastName("Mo").role("consumer").status("ACTIVE")
            .organisation(OrganisationEntity.builder().id(1).name("Old").build()).build();
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(before));

        Caller caller = service.caller("Bearer t");

        assertThat(caller.user().getFirstName()).isEqualTo("Deirdre");
        assertThat(caller.user().getOrganisation()).isEqualTo(ORG);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void suspended_person_is_refused_even_on_an_approved_domain() {
        signedInAs("dee@example.gov.uk", "Dee Mo");
        listed("example.gov.uk");
        UserEntity suspended = UserEntity.builder().id(3).entraObjectId(OID).email("dee@example.gov.uk")
            .firstName("Dee").lastName("Mo").role("consumer").status("SUSPENDED").organisation(ORG).build();
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.of(suspended));

        ResponseStatusException refused = refusal(() -> service.caller("Bearer t"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.getReason()).contains("suspended");
    }

    @Test
    void an_address_already_used_by_another_account_is_never_linked() {
        signedInAs("dee@example.gov.uk", "Dee Mo");
        listed("example.gov.uk");
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("dee@example.gov.uk"))
            .thenReturn(Optional.of(UserEntity.builder().id(9).build()));

        ResponseStatusException refused = refusal(() -> service.caller("Bearer t"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void sign_in_with_no_address_is_refused() {
        signedInAs(null, "Dee");

        assertThat(refusal(() -> service.caller("Bearer t")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void two_first_calls_at_once_end_up_with_one_person() {
        signedInAs("dee@example.gov.uk", "Dee Mo");
        listed("example.gov.uk");
        UserEntity winner = UserEntity.builder().id(3).entraObjectId(OID).status("ACTIVE").build();
        when(userRepository.findByEntraObjectId(OID)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(userRepository.save(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.caller("Bearer t").user()).isEqualTo(winner);
    }

    @Test
    void nothing_exists_while_the_switch_is_off() {
        ReflectionTestUtils.setField(service, "mode", "off");

        assertThat(service.enabled()).isFalse();
        assertThat(refusal(() -> service.caller("Bearer t")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> service.admin("Bearer t")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(verifier, never()).verify(anyString());
    }

    @Test
    void only_on_or_off_lets_the_service_start() {
        ReflectionTestUtils.setField(service, "mode", " ON ");
        assertThatCode(service::requireKnownMode).doesNotThrowAnyException();
        assertThat(service.enabled()).isTrue();

        ReflectionTestUtils.setField(service, "mode", "maybe");
        assertThatThrownBy(service::requireKnownMode)
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("maybe");
    }

    @Test
    void admin_endpoints_look_like_they_do_not_exist_to_anyone_else() {
        signedInAs("dee@example.gov.uk", "Dee Mo");

        assertThat(refusal(() -> service.admin("Bearer t")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void admins_are_matched_on_email_without_regard_to_case_or_spaces_and_need_no_approved_domain() {
        signedInAs("ADMIN@example.gov.uk", "An Admin");

        assertThat(service.admin("Bearer t").email()).isEqualTo("ADMIN@example.gov.uk");
        verify(domainRepository, never()).findByDomainIgnoreCase(anyString());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void local_accounts_address_is_checked_against_the_list_only_when_the_switch_is_on() {
        ReflectionTestUtils.setField(service, "mode", "off");
        service.requireApprovedDomain("anyone@unknown.example");
        verify(domainRepository, never()).findByDomainIgnoreCase(anyString());

        ReflectionTestUtils.setField(service, "mode", "on");
        listed("example.gov.uk");
        service.requireApprovedDomain("Dee@Example.gov.uk");
        when(domainRepository.findByDomainIgnoreCase("unknown.example")).thenReturn(Optional.empty());

        ResponseStatusException refused = refusal(() -> service.requireApprovedDomain("dee@unknown.example"));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.getReason()).startsWith("DOMAIN_NOT_APPROVED").contains("unknown.example");
        assertThat(refusal(() -> service.requireApprovedDomain("not-an-address")).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal(() -> service.requireApprovedDomain(null)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void profile_names_the_organisation() {
        signedInAs("dee@example.gov.uk", "Dee Mo");
        listed("example.gov.uk");

        assertThat(service.profile(service.caller("Bearer t")).organisation()).isEqualTo("Example Council");
    }

    @Test
    void names_are_split_into_first_and_the_rest() {
        assertThat(OrganisationApprovalService.splitName("Dee Mo Jones", "x@y.z")).containsExactly("Dee", "Mo Jones");
        assertThat(OrganisationApprovalService.splitName("Dee", "x@y.z")).containsExactly("Dee", "");
        assertThat(OrganisationApprovalService.splitName("  ", "dee@y.z")).containsExactly("dee", "");
        assertThat(OrganisationApprovalService.splitName(null, "dee@y.z")).containsExactly("dee", "");
    }
}
