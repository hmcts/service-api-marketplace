package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.AdminUserResponse;
import uk.gov.hmcts.cp.entity.OrganisationDomainEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.repository.OrganisationDomainRepository;
import uk.gov.hmcts.cp.repository.OrganisationRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganisationAdminServiceTest {

    private static final OrganisationEntity ORG = OrganisationEntity.builder().id(7).name("Example Council").build();
    private static final String ADMIN = "admin@example.gov.uk";

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private OrganisationDomainRepository domainRepository;

    @Mock
    private OrganisationApprovalService approvals;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ClockService clockService;

    private OrganisationAdminService service;

    @BeforeEach
    void setUp() {
        service = new OrganisationAdminService(userRepository, organisationRepository, domainRepository, approvals,
            transactionTemplate, clockService);
        lenient().when(clockService.now()).thenReturn(Instant.parse("2026-10-14T09:00:00Z"));
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(organisationRepository.findById(7)).thenReturn(Optional.of(ORG));
        lenient().when(domainRepository.findByOrganisationIdOrderByDomainAsc(anyInt())).thenReturn(List.of());
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private UserEntity person(final String status) {
        return UserEntity.builder().id(3).email("dee@example.gov.uk").firstName("Dee").lastName("Mo").role("consumer")
            .status(status).build();
    }

    private ResponseStatusException refusal(final Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run);
    }

    @Test
    void adding_a_domain_lists_it_lower_case_and_activates_people_held_on_it() {
        UserEntity held = person("HELD");
        when(domainRepository.findByDomainIgnoreCase("example.gov.uk")).thenReturn(Optional.empty());
        when(userRepository.findByStatusAndEmailEndingWithIgnoreCase("HELD", "@example.gov.uk"))
            .thenReturn(List.of(held));

        service.addDomain(7, " Example.GOV.uk ", ADMIN);

        ArgumentCaptor<OrganisationDomainEntity> domain = ArgumentCaptor.forClass(OrganisationDomainEntity.class);
        verify(domainRepository).save(domain.capture());
        assertThat(domain.getValue().getDomain()).isEqualTo("example.gov.uk");
        assertThat(domain.getValue().getOrganisation()).isEqualTo(ORG);
        ArgumentCaptor<UserEntity> activated = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(activated.capture());
        assertThat(activated.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(activated.getValue().getOrganisation()).isEqualTo(ORG);
        assertThat(activated.getValue().getApprovedBy()).isEqualTo(ADMIN);
        assertThat(activated.getValue().getApprovedAt()).isNotNull();
    }

    @Test
    void domains_that_are_invalid_shared_or_already_listed_are_refused_and_nothing_is_saved() {
        when(domainRepository.findByDomainIgnoreCase("taken.example")).thenReturn(Optional.of(
            OrganisationDomainEntity.builder().domain("taken.example").build()));

        assertThat(refusal(() -> service.addDomain(7, "not a domain", ADMIN)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal(() -> service.addDomain(7, "gmail.com", ADMIN)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal(() -> service.addDomain(7, "taken.example", ADMIN)).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(refusal(() -> service.addDomain(99, "new.example", ADMIN)).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        verify(domainRepository, never()).save(any(OrganisationDomainEntity.class));
    }

    @Test
    void organisations_need_a_name_and_a_new_one() {
        when(organisationRepository.findByNameIgnoreCase("Taken")).thenReturn(Optional.of(ORG));
        when(organisationRepository.save(any(OrganisationEntity.class))).thenReturn(ORG);

        assertThat(refusal(() -> service.createOrganisation("  ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal(() -> service.createOrganisation("x".repeat(121))).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal(() -> service.createOrganisation("Taken")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(service.createOrganisation("Example Council").name()).isEqualTo("Example Council");
    }

    @Test
    void approving_places_the_person_in_the_organisation_and_grants_a_requested_producer_role() {
        UserEntity asked = person("HELD").toBuilder().requestedRole("producer").build();
        when(userRepository.findById(3)).thenReturn(Optional.of(asked));

        AdminUserResponse result = service.approve(3, 7, ADMIN);

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getValue().getRole()).isEqualTo("producer");
        assertThat(saved.getValue().getRequestedRole()).isNull();
        assertThat(saved.getValue().getApprovedBy()).isEqualTo(ADMIN);
        assertThat(result.organisation()).isEqualTo("Example Council");
    }

    @Test
    void approving_someone_or_somewhere_that_does_not_exist_is_not_found() {
        when(userRepository.findById(3)).thenReturn(Optional.of(person("HELD")));
        when(userRepository.findById(4)).thenReturn(Optional.empty());

        assertThat(refusal(() -> service.approve(4, 7, ADMIN)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> service.approve(3, 99, ADMIN)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> service.approve(null, 7, ADMIN)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void suspending_records_who_and_when() {
        UserEntity active = person("ACTIVE").toBuilder().organisation(ORG).build();
        when(userRepository.findById(3)).thenReturn(Optional.of(active));

        AdminUserResponse result = service.suspend(3, ADMIN);

        assertThat(result.status()).isEqualTo("SUSPENDED");
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getApprovedBy()).isEqualTo(ADMIN);
    }

    @Test
    void reinstating_goes_back_to_active_with_an_organisation_and_otherwise_to_the_domain_rule() {
        UserEntity placed = person("SUSPENDED").toBuilder().organisation(ORG).build();
        when(userRepository.findById(3)).thenReturn(Optional.of(placed));
        when(approvals.applyDomainRule(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.reinstate(3, ADMIN).status()).isEqualTo("ACTIVE");

        when(userRepository.findById(3)).thenReturn(Optional.of(person("SUSPENDED")));
        assertThat(service.reinstate(3, ADMIN).status()).isEqualTo("HELD");
    }

    @Test
    void the_queue_defaults_to_held_and_shows_each_persons_domain() {
        when(userRepository.findByStatusOrderByIdAsc("HELD")).thenReturn(List.of(person("HELD")));

        assertThat(service.users(null).users()).singleElement().satisfies(user -> {
            assertThat(user.domain()).isEqualTo("example.gov.uk");
            assertThat(user.name()).isEqualTo("Dee Mo");
            assertThat(user.organisation()).isNull();
        });
        service.users(" suspended ");
        verify(userRepository).findByStatusOrderByIdAsc("SUSPENDED");
    }

    @Test
    void organisations_list_their_domains() {
        when(organisationRepository.findAll()).thenReturn(List.of(ORG));
        when(domainRepository.findByOrganisationIdOrderByDomainAsc(7)).thenReturn(List.of(
            OrganisationDomainEntity.builder().domain("example.gov.uk").build()));

        assertThat(service.organisations().organisations()).singleElement()
            .satisfies(org -> assertThat(org.domains()).containsExactly("example.gov.uk"));
    }
}
