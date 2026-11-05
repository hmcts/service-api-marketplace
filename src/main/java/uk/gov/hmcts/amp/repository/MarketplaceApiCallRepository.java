package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.MarketplaceApiCallEntity;

import java.util.List;

@Repository
public interface MarketplaceApiCallRepository extends JpaRepository<MarketplaceApiCallEntity, Long> {

    List<MarketplaceApiCallEntity> findAllByOrderByShortCodeAscPositionAsc();
}
