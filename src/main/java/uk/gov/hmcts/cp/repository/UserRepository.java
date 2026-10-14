package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entity.UserEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByEntraObjectId(String entraObjectId);

    List<UserEntity> findByStatusOrderByIdAsc(String status);

    List<UserEntity> findByStatusAndEmailEndingWithIgnoreCase(String status, String emailEnding);

    List<UserEntity> findByOrganisationIdOrderByEmailAsc(Integer organisationId);
}
