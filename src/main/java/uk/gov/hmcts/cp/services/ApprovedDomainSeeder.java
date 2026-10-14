package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.entity.OrganisationDomainEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.repository.OrganisationDomainRepository;
import uk.gov.hmcts.cp.repository.OrganisationRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

/**
 * Loads the list of pre-approved domains from APPROVED_DOMAINS at startup, so the list can be set by configuration
 * and needs nobody to click anything. Format: organisations separated by {@code ;}, each as
 * {@code Organisation name=domain,domain}, for example
 * {@code HMCTS=hmcts.net,justice.gov.uk;Example Council=example.gov.uk}. Adding what is already there does
 * nothing, so it is safe on every start. A badly written entry stops the service starting rather than being
 * ignored, and a shared mail provider is never accepted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovedDomainSeeder implements ApplicationRunner {

    private final OrganisationApprovalService approvals;
    private final OrganisationRepository organisationRepository;
    private final OrganisationDomainRepository domainRepository;
    private final ClockService clockService;

    @Value("${APPROVED_DOMAINS:}")
    private String approved = "";

    @Override
    public void run(final ApplicationArguments args) {
        if (!approvals.enabled() || approved == null || approved.isBlank()) {
            return;
        }
        for (String entry : approved.split(";")) {
            if (!entry.isBlank()) {
                seed(entry.trim());
            }
        }
    }

    private void seed(final String entry) {
        int equals = entry.indexOf('=');
        if (equals < 1) {
            throw new IllegalStateException("APPROVED_DOMAINS entries look like 'Organisation=domain,domain', not '"
                + entry + "'");
        }
        String name = entry.substring(0, equals).trim();
        OrganisationEntity organisation = organisationRepository.findByNameIgnoreCase(name)
            .orElseGet(() -> organisationRepository.save(OrganisationEntity.builder().name(name).build()));
        for (String raw : entry.substring(equals + 1).split(",")) {
            String domain = EmailDomain.normalise(raw).orElseThrow(() ->
                new IllegalStateException("APPROVED_DOMAINS has an invalid domain: '" + raw.trim() + "'"));
            if (EmailDomain.isShared(domain)) {
                throw new IllegalStateException("APPROVED_DOMAINS cannot include a shared mail provider: " + domain);
            }
            if (domainRepository.findByDomainIgnoreCase(domain).isEmpty()) {
                domainRepository.save(OrganisationDomainEntity.builder().organisation(organisation).domain(domain)
                    .createdAt(LocalDateTime.ofInstant(clockService.now(), ZoneId.systemDefault())).build());
                log.info("Domain {} approved for organisation {} from configuration", domain, name);
            }
        }
        log.info("Approved domains loaded for {}: {}", name, Arrays.toString(entry.substring(equals + 1).split(",")));
    }
}
