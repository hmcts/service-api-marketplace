package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.cp.entity.OrganisationDomainEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.repository.OrganisationDomainRepository;
import uk.gov.hmcts.cp.repository.OrganisationRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovedDomainSeederTest {

    @Mock
    private OrganisationApprovalService approvals;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private OrganisationDomainRepository domainRepository;

    @Mock
    private ClockService clockService;

    private ApprovedDomainSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new ApprovedDomainSeeder(approvals, organisationRepository, domainRepository, clockService);
        lenient().when(approvals.enabled()).thenReturn(true);
        lenient().when(clockService.now()).thenReturn(Instant.parse("2026-10-14T09:00:00Z"));
        lenient().when(organisationRepository.save(any(OrganisationEntity.class))).thenAnswer(invocation ->
            ((OrganisationEntity) invocation.getArgument(0)).toBuilder().id(1).build());
        lenient().when(domainRepository.findByDomainIgnoreCase(any())).thenReturn(Optional.empty());
    }

    private void configured(final String value) {
        ReflectionTestUtils.setField(seeder, "approved", value);
    }

    @Test
    void organisations_and_their_domains_are_created_from_the_configuration() {
        configured("HMCTS=hmcts.net,Justice.gov.uk; Example Council=example.gov.uk");
        when(organisationRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());

        seeder.run(null);

        ArgumentCaptor<OrganisationDomainEntity> saved = ArgumentCaptor.forClass(OrganisationDomainEntity.class);
        verify(domainRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(OrganisationDomainEntity::getDomain)
            .containsExactly("hmcts.net", "justice.gov.uk", "example.gov.uk");
        verify(organisationRepository, times(2)).save(any(OrganisationEntity.class));
    }

    @Test
    void running_it_again_adds_nothing_that_is_already_there() {
        configured("HMCTS=hmcts.net");
        when(organisationRepository.findByNameIgnoreCase("HMCTS")).thenReturn(Optional.of(
            OrganisationEntity.builder().id(1).name("HMCTS").build()));
        when(domainRepository.findByDomainIgnoreCase("hmcts.net")).thenReturn(Optional.of(
            OrganisationDomainEntity.builder().domain("hmcts.net").build()));

        seeder.run(null);

        verify(domainRepository, never()).save(any(OrganisationDomainEntity.class));
        verify(organisationRepository, never()).save(any(OrganisationEntity.class));
    }

    @Test
    void nothing_happens_when_the_switch_is_off_or_nothing_is_configured() {
        configured("HMCTS=hmcts.net");
        when(approvals.enabled()).thenReturn(false);
        seeder.run(null);

        when(approvals.enabled()).thenReturn(true);
        configured("  ");
        seeder.run(null);

        verify(organisationRepository, never()).save(any(OrganisationEntity.class));
        verify(domainRepository, never()).save(any(OrganisationDomainEntity.class));
    }

    @Test
    void badly_written_entry_stops_the_service_starting() {
        configured("no-equals-sign");
        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no-equals-sign");

        configured("HMCTS=not a domain");
        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not a domain");
    }

    @Test
    void shared_mail_provider_is_never_accepted() {
        configured("Everyone=gmail.com");

        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("gmail.com");
        verify(domainRepository, never()).save(any(OrganisationDomainEntity.class));
    }
}
