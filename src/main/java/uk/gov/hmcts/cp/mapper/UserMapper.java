package uk.gov.hmcts.cp.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.cp.domain.UserResponse;
import uk.gov.hmcts.cp.entity.UserEntity;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public abstract class UserMapper {

    @Mapping(target = "orgName", source = "organisation.name")
    public abstract UserResponse fromEntity(UserEntity entity);
}
