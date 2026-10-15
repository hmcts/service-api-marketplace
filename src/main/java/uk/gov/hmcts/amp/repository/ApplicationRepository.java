package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.ApplicationEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    List<ApplicationEntity> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<ApplicationEntity> findByClientId(String clientId);

    boolean existsByUserIdAndAppname(Long userId, String appname);
}
