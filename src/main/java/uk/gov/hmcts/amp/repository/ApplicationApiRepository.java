package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationApiRepository extends JpaRepository<ApplicationApiEntity, Long> {

    interface ApplicationApiCount {
        Long getApplicationId();

        long getTotal();
    }

    @Query("select a.application.id as applicationId, count(a) as total from ApplicationApiEntity a"
        + " group by a.application.id")
    List<ApplicationApiCount> countByApplication();

    List<ApplicationApiEntity> findByApplicationIdOrderBySubscribedAtAsc(Long applicationId);

    List<ApplicationApiEntity> findByApplicationId(Long applicationId);

    long countByApplicationId(Long applicationId);

    List<ApplicationApiEntity> findByShortCode(String shortCode);

    boolean existsByApplicationIdAndShortCode(Long applicationId, String shortCode);

    Optional<ApplicationApiEntity> findBySubscriptionName(String subscriptionName);
}
