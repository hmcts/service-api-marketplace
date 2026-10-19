package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;

import java.util.List;

@Repository
public interface ProductionRequestRepository extends JpaRepository<ProductionRequestEntity, Long> {

    List<ProductionRequestEntity> findByUserIdOrderBySubmittedAtDesc(Long userId);

    boolean existsByReference(String reference);
}
