package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationTeamMemberEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationTeamMemberRepository extends JpaRepository<ApplicationTeamMemberEntity, Long> {

    List<ApplicationTeamMemberEntity> findByApplicationOrderByAddedAtAsc(ApplicationEntity application);

    Optional<ApplicationTeamMemberEntity> findByApplicationAndEmailIgnoreCase(ApplicationEntity application,
        String email);

    Optional<ApplicationTeamMemberEntity> findByPublicIdAndApplication(UUID publicId, ApplicationEntity application);
}
