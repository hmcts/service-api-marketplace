package uk.gov.hmcts.amp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.amp.domain.CallVolume;
import uk.gov.hmcts.amp.domain.DataCombined;
import uk.gov.hmcts.amp.domain.DataRecipient;
import uk.gov.hmcts.amp.domain.OrganisationType;
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
    @Mapping(source = "request.organisation", target = "consumerTeam")
    @Mapping(source = "request.goLiveDate", target = "goLiveDate")
    @Mapping(source = "request.callVolume", target = "expectedCallVolumes")
    @Mapping(source = "apis", target = "apis")
    @Mapping(source = "request.dataRequest", target = "dataRequestDescription")
    @Mapping(source = "request.proposedOutputs", target = "proposedOutputs")
    @Mapping(source = "request.organisationType", target = "organisationType")
    @Mapping(source = "request.dataRecipients", target = "dataRecipients")
    @Mapping(source = "request.dataRecipientsExplanation", target = "dataRecipientsExplanation")
    @Mapping(source = "request.dataCombined", target = "dataCombined")
    @Mapping(source = "request.dataCombinedDescription", target = "dataCombinedDescription")
    @Mapping(source = "request.reference", target = "reference")
    @Mapping(target = "personalisation", ignore = true)
    ProductionAccessReviewEmail toReviewEmail(ProductionRequestEntity request, UserEntity user,
                                              List<String> apis, String inbox);

    // Without these MapStruct reaches for Enum.valueOf, which is named after the Java constant
    // and so rejects every slug we actually store: valueOf("other-government") throws.
    default CallVolume callVolume(final String slug) {
        return CallVolume.fromSlug(slug);
    }

    default OrganisationType organisationType(final String slug) {
        return OrganisationType.fromSlug(slug);
    }

    default DataCombined dataCombined(final String slug) {
        return DataCombined.fromSlug(slug);
    }

    default DataRecipient dataRecipient(final String slug) {
        return DataRecipient.fromSlug(slug);
    }

    default String displayName(final UserEntity user) {
        if (user == null) {
            return null;
        }
        final String first = user.getFirstName() == null ? "" : user.getFirstName();
        final String last = user.getLastName() == null ? "" : user.getLastName();
        return (first + " " + last).trim();
    }
}
