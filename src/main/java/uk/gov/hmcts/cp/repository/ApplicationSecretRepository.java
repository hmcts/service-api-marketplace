package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationSecretEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationSecretRepository extends JpaRepository<ApplicationSecretEntity, Long> {

    List<ApplicationSecretEntity> findByApplicationOrderByCreatedAtDesc(ApplicationEntity application);

    Optional<ApplicationSecretEntity> findByPublicIdAndApplication(UUID publicId, ApplicationEntity application);
}
