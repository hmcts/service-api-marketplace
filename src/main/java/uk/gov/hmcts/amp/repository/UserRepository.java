package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.UserEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEntraObjectId(String entraObjectId);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findByStatusOrderByCreatedAtDesc(String status);
}
