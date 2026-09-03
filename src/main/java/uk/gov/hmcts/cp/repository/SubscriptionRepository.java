package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.SubscriptionRequestEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<SubscriptionRequestEntity, UUID> {

    List<SubscriptionRequestEntity> findByUserEmail(String userEmail);
}
