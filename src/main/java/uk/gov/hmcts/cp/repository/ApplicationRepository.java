package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.ApplicationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    List<ApplicationEntity> findByUserId(Integer userId);

    Optional<ApplicationEntity> findByPublicId(UUID publicId);

    boolean existsByUserIdAndNameIgnoreCaseAndEnvironment(Integer userId, String name, String environment);

    /** Applications the caller owns, plus those they have been invited onto (matched on email). */
    @Query("select a from ApplicationEntity a where a.user.id = :userId "
        + "or exists (select 1 from ApplicationTeamMemberEntity m "
        + "where m.application = a and lower(m.email) = lower(:email)) "
        + "order by a.createdAt desc")
    List<ApplicationEntity> findAccessibleTo(@Param("userId") Integer userId, @Param("email") String email);
}
