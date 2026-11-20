package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.ApplicationEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    interface UserApplicationCount {
        Long getUserId();

        long getTotal();
    }

    @Query("select a.user.id as userId, count(a) as total from ApplicationEntity a group by a.user.id")
    List<UserApplicationCount> countByUser();

    List<ApplicationEntity> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<ApplicationEntity> findByClientId(String clientId);

    boolean existsByUserIdAndAppname(Long userId, String appname);
}
