package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;

import java.util.List;

@Repository
public interface ApplicationApiRepository extends JpaRepository<ApplicationApiEntity, Long> {

    List<ApplicationApiEntity> findByApplicationId(Long applicationId);

    List<ApplicationApiEntity> findByShortCode(String shortCode);
}
