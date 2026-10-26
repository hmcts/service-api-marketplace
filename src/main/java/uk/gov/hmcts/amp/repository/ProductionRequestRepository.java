package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionRequestRepository extends JpaRepository<ProductionRequestEntity, Long> {

    List<ProductionRequestEntity> findByUserIdOrderBySubmittedAtDesc(Long userId);

    Optional<ProductionRequestEntity> findByUserIdAndReference(Long userId, String reference);

    boolean existsByReference(String reference);

    boolean existsByApplicationId(Long applicationId);
}
