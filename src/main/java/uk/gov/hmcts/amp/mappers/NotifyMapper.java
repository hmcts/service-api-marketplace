package uk.gov.hmcts.amp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessRequestEmail;
import uk.gov.hmcts.amp.notify.templates.ProductionAccessReviewEmail;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NotifyMapper {

    @Mapping(source = "user.email", target = "emailAddress")
    @Mapping(target = "consumerName", expression = "java(displayName(user))")
    @Mapping(source = "request.applicationName", target = "applicationName")
    @Mapping(source = "apis", target = "apis")
    @Mapping(source = "request.reference", target = "reference")
    @Mapping(target = "personalisation", ignore = true)
    ProductionAccessRequestEmail toRequesterEmail(ProductionRequestEntity request, UserEntity user,
                                                  List<String> apis);

    @Mapping(source = "inbox", target = "emailAddress")
    @Mapping(source = "request.applicationName", target = "applicationName")
    @Mapping(source = "request.submittedAt", target = "submittedAt")
    @Mapping(target = "consumerName", expression = "java(displayName(user))")
    @Mapping(source = "user.email", target = "consumerEmailAddress")
    @Mapping(source = "request.organisation", target = "consumerOrganisation")
    @Mapping(source = "request.goLiveDate", target = "goLiveDate")
    @Mapping(source = "request.callVolume", target = "expectedCallVolumes")
    @Mapping(source = "apis", target = "apis")
    @Mapping(source = "request.dataRequest", target = "dataRequestDescription")
    @Mapping(source = "request.proposedOutputs", target = "proposedOutputs")
    @Mapping(source = "request.reference", target = "reference")
    @Mapping(target = "personalisation", ignore = true)
    ProductionAccessReviewEmail toReviewEmail(ProductionRequestEntity request, UserEntity user,
                                              List<String> apis, String inbox);

    default String displayName(final UserEntity user) {
        if (user == null) {
            return null;
        }
        final String first = user.getFirstName() == null ? "" : user.getFirstName();
        final String last = user.getLastName() == null ? "" : user.getLastName();
        return (first + " " + last).trim();
    }
}
