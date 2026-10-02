package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.ApplicationApiKeyEntity;

import java.util.List;

@Repository
public interface ApplicationApiKeyRepository extends JpaRepository<ApplicationApiKeyEntity, Long> {

    List<ApplicationApiKeyEntity> findByApplicationId(Long applicationId);
}
