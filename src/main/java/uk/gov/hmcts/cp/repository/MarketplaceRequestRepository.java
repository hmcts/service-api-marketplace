package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.MarketplaceRequestEntity;

import java.util.UUID;

@Repository
public interface MarketplaceRequestRepository extends JpaRepository<MarketplaceRequestEntity, UUID> {
}
