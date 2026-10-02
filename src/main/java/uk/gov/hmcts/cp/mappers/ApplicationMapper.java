package uk.gov.hmcts.cp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.cp.domain.ApiCredential;
import uk.gov.hmcts.cp.domain.ApplicationResponse;
import uk.gov.hmcts.cp.entity.ApplicationApiKeyEntity;
import uk.gov.hmcts.cp.entity.ApplicationEntity;

import java.util.List;

/**
 * unmappedTargetPolicy = ERROR is the point: adding a column to either entity or a field to
 * either response without mapping it fails the build rather than silently persisting null.
 */
@Mapper(
    componentModel = "spring",
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class ApplicationMapper {

    public abstract ApiCredential fromEntity(ApplicationApiKeyEntity entity);

    public abstract List<ApiCredential> fromEntities(List<ApplicationApiKeyEntity> entities);

    @Mapping(target = "id", source = "application.id")
    @Mapping(target = "name", source = "application.name")
    @Mapping(target = "environment", source = "application.environment")
    @Mapping(target = "clientId", source = "application.clientId")
    @Mapping(target = "apiCredentials", source = "apiKeys")
    public abstract ApplicationResponse toResponse(
        ApplicationEntity application, List<ApplicationApiKeyEntity> apiKeys, String clientSecret);
}
