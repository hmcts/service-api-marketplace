package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;

import java.util.List;

@Repository
public interface MarketplaceApiRepository extends JpaRepository<MarketplaceApiEntity, String> {

    List<MarketplaceApiEntity> findAllByOrderByShortCodeAsc();
}
