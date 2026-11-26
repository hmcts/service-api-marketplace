package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ProductionRequestRepository extends JpaRepository<ProductionRequestEntity, Long> {

    List<ProductionRequestEntity> findByUserIdOrderBySubmittedAtDesc(Long userId);

    List<ProductionRequestEntity> findByApplicationIdOrderBySubmittedAtDesc(Long applicationId);

    @Query("select distinct p.application.id from ProductionRequestEntity p where p.application is not null")
    Set<Long> findRequestedApplicationIds();

    Optional<ProductionRequestEntity> findByUserIdAndReference(Long userId, String reference);

    boolean existsByReference(String reference);

    boolean existsByApplicationId(Long applicationId);
}
