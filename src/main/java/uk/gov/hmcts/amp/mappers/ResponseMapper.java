package uk.gov.hmcts.amp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.amp.domain.ApiSubscriptionResponse;
import uk.gov.hmcts.amp.domain.ApplicationResponse;
import uk.gov.hmcts.amp.domain.ProductionAccessDetail;
import uk.gov.hmcts.amp.domain.ProductionAccessResponse;
import uk.gov.hmcts.amp.domain.UserResponse;
import uk.gov.hmcts.amp.openapi.model.CatalogueDetail;
import uk.gov.hmcts.amp.openapi.model.CatalogueEntry;
import uk.gov.hmcts.amp.openapi.model.ApiSubscription;
import uk.gov.hmcts.amp.openapi.model.Application;
import uk.gov.hmcts.amp.openapi.model.CreatedApplication;
import uk.gov.hmcts.amp.openapi.model.ProductionRequest;
import uk.gov.hmcts.amp.openapi.model.ProductionRequestDetail;
import uk.gov.hmcts.amp.openapi.model.SubscriptionKey;
import uk.gov.hmcts.amp.openapi.model.User;

import java.util.List;

/** Domain records to the models generated from the spec. A spec field no record fills fails the build. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResponseMapper {

    User toUser(UserResponse user);

    List<User> toUsers(List<UserResponse> users);

    Application toApplication(ApplicationResponse application);

    List<Application> toApplications(List<ApplicationResponse> applications);

    CreatedApplication toCreatedApplication(ApplicationResponse application);

    ProductionRequest toProductionRequest(ProductionAccessResponse request);

    List<ProductionRequest> toProductionRequests(List<ProductionAccessResponse> requests);

    ProductionRequestDetail toProductionRequestDetail(ProductionAccessDetail request);



    List<CatalogueEntry> toCatalogueEntries(List<uk.gov.hmcts.amp.domain.CatalogueEntry> entries);

    CatalogueDetail toCatalogueDetail(uk.gov.hmcts.amp.domain.CatalogueDetail detail);

    ApiSubscription toApiSubscription(ApiSubscriptionResponse subscription);

    SubscriptionKey toSubscriptionKey(uk.gov.hmcts.amp.domain.SubscriptionKey key);

    List<SubscriptionKey> toSubscriptionKeys(List<uk.gov.hmcts.amp.domain.SubscriptionKey> keys);

    default ProductionRequestDetail.CallVolumeEnum callVolume(final String callVolume) {
        return callVolume == null ? null : ProductionRequestDetail.CallVolumeEnum.fromValue(callVolume);
    }
}
