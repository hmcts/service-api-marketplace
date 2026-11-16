package uk.gov.hmcts.amp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.amp.entity.AdminEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<AdminEntity, Long> {

    boolean existsByEntraObjectId(UUID entraObjectId);

    Optional<AdminEntity> findByEntraObjectId(UUID entraObjectId);
}
