package uk.gov.hmcts.cp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import uk.gov.hmcts.cp.domain.PublishRequest;
import uk.gov.hmcts.cp.domain.PublishResponse;
import uk.gov.hmcts.cp.entity.PublishRequestEntity;
import uk.gov.hmcts.cp.entity.UserEntity;

/**
 * unmappedTargetPolicy = ERROR is the point: adding a column to the entity or a field to
 * the response without mapping it fails the build rather than silently persisting null.
 * Anything genuinely not the mapper's to set is ignored explicitly below.
 */
@Mapper(
    componentModel = "spring",
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class PublishMapper {

    @Mapping(target = "reference", ignore = true)
    @Mapping(target = "isNew", ignore = true)
    @Mapping(target = "orgName", source = "orgName")
    @Mapping(target = "userName", expression = "java(firstName + \" \" + lastName)")
    @Mapping(target = "userEmail", source = "userEmail")
    @Mapping(target = "status", constant = "NEW")
    @Mapping(target = "submittedAt", ignore = true)
    public abstract PublishRequestEntity toEntity(
        int userId,
        String orgName,
        String userEmail,
        String firstName,
        String lastName,
        PublishRequest request);

    @Mapping(target = "reference", source = "requestEntity.reference")
    @Mapping(target = "status", source = "requestEntity.status")
    @Mapping(target = "requestingOrgName", source = "user.organisation.name")
    @Mapping(target = "requestingUserName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
    @Mapping(target = "requestingUserEmail", source = "user.email")
    public abstract PublishResponse fromEntity(UserEntity user, PublishRequestEntity requestEntity);
}
