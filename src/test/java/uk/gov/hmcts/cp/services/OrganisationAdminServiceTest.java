package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
    private ClockService clockService;

    private OrganisationAdminService service;

    @BeforeEach
    void setUp() {
        service = new OrganisationAdminService(userRepository, organisationRepository, domainRepository, clockService);
        lenient().when(clockService.now()).thenReturn(Instant.parse("2026-10-14T09:00:00Z"));
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(organisationRepository.findById(7)).thenReturn(Optional.of(ORG));
        lenient().when(domainRepository.findByOrganisationIdOrderByDomainAsc(anyInt())).thenReturn(List.of());
    }

    private UserEntity person(final String status) {
        return UserEntity.builder().id(3).email("dee@example.gov.uk").firstName("Dee").lastName("Mo").role("consumer")
            .status(status).organisation(ORG).build();
    }

    private ResponseStatusException refusal(final Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run);
    }

    @Test
    void adding_a_domain_approves_it_lower_case_for_the_organisation() {
        when(domainRepository.findByDomainIgnoreCase("example.gov.uk")).thenReturn(Optional.empty());

        service.addDomain(7, " Example.GOV.uk ");

        ArgumentCaptor<OrganisationDomainEntity> domain = ArgumentCaptor.forClass(OrganisationDomainEntity.class);
        verify(domainRepository).save(domain.capture());
        assertThat(domain.getValue().getDomain()).isEqualTo("example.gov.uk");
        assertThat(domain.getValue().getOrganisation()).isEqualTo(ORG);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void domains_that_are_invalid_shared_or_already_listed_are_refused_and_nothing_is_saved() {
        when(domainRepository.findByDomainIgnoreCase("taken.example")).thenReturn(Optional.of(
            OrganisationDomainEntity.builder().domain("taken.example").build()));

        assertThat(refusal(() -> service.addDomain(7, "not a domain")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal(() -> service.addDomain(7, "gmail.com")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal(() -> service.addDomain(7, "taken.example")).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(refusal(() -> service.addDomain(99, "new.example")).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        verify(domainRepository, never()).save(any(OrganisationDomainEntity.class));
    }

    @Test
    void taking_a_domain_off_the_list_removes_only_that_organisations_entry() {
        OrganisationDomainEntity entry = OrganisationDomainEntity.builder().domain("example.gov.uk")
            .organisation(ORG).build();
        OrganisationDomainEntity others = OrganisationDomainEntity.builder().domain("other.example")
            .organisation(OrganisationEntity.builder().id(8).name("Other").build()).build();
        when(domainRepository.findByDomainIgnoreCase("example.gov.uk")).thenReturn(Optional.of(entry));
        when(domainRepository.findByDomainIgnoreCase("other.example")).thenReturn(Optional.of(others));

        service.removeDomain(7, "example.gov.uk");

        verify(domainRepository).delete(entry);
        assertThat(refusal(() -> service.removeDomain(7, "other.example")).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> service.removeDomain(7, null)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
    void suspending_and_reinstating_record_who_and_when() {
        when(userRepository.findById(3)).thenReturn(Optional.of(person("ACTIVE")));

        AdminUserResponse suspended = service.suspend(3, ADMIN);
        AdminUserResponse reinstated = service.reinstate(3, ADMIN);

        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(reinstated.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(user -> {
            assertThat(user.getApprovedBy()).isEqualTo(ADMIN);
            assertThat(user.getApprovedAt()).isNotNull();
        });
    }

    @Test
    void someone_who_does_not_exist_is_not_found() {
        when(userRepository.findById(4)).thenReturn(Optional.empty());

        assertThat(refusal(() -> service.suspend(4, ADMIN)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refusal(() -> service.reinstate(null, ADMIN)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void the_people_list_defaults_to_active_and_shows_each_persons_domain_and_organisation() {
        when(userRepository.findByStatusOrderByIdAsc("ACTIVE")).thenReturn(List.of(person("ACTIVE")));

        assertThat(service.users(null).users()).singleElement().satisfies(user -> {
            assertThat(user.domain()).isEqualTo("example.gov.uk");
            assertThat(user.name()).isEqualTo("Dee Mo");
            assertThat(user.organisation()).isEqualTo("Example Council");
        });
        service.users(" suspended ");
        verify(userRepository).findByStatusOrderByIdAsc("SUSPENDED");
    }

    @Test
    void an_organisations_people_and_domains_can_be_listed() {
        when(organisationRepository.findAll()).thenReturn(List.of(ORG));
        when(domainRepository.findByOrganisationIdOrderByDomainAsc(7)).thenReturn(List.of(
            OrganisationDomainEntity.builder().domain("example.gov.uk").build()));
        when(userRepository.findByOrganisationIdOrderByEmailAsc(7)).thenReturn(List.of(person("ACTIVE")));

        assertThat(service.organisations().organisations()).singleElement()
            .satisfies(org -> assertThat(org.domains()).containsExactly("example.gov.uk"));
        assertThat(service.usersOf(7).users()).hasSize(1);
        assertThat(refusal(() -> service.usersOf(99)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
