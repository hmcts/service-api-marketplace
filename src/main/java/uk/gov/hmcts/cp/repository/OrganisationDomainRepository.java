package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.OrganisationDomainEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganisationDomainRepository extends JpaRepository<OrganisationDomainEntity, Integer> {

    Optional<OrganisationDomainEntity> findByDomainIgnoreCase(String domain);

    List<OrganisationDomainEntity> findByOrganisationIdOrderByDomainAsc(Integer organisationId);
}
