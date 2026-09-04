package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import uk.gov.hmcts.cp.entity.PublishRequestEntity;

@Repository
public interface PublishRepository extends JpaRepository<PublishRequestEntity, UUID> {

    List<PublishRequestEntity> findByUserEmail(String userEmail);

    Optional<PublishRequestEntity> findByReference(String reference);
}
