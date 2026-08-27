package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.OrganisationEntity;

@Repository
public interface OrganisationRepository extends JpaRepository<OrganisationEntity, Integer> {
}
