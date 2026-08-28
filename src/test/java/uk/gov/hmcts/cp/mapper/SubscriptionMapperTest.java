package uk.gov.hmcts.cp.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.MarketplaceRequestEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SubscriptionMapperTest {

    @InjectMocks
    private SubscriptionMapperImpl mapper;

    private static final UserEntity USER = UserEntity.builder()
        .organisation(OrganisationEntity.builder().name("Test Org").build())
        .firstName("Joe")
        .lastName("Bloggs")
        .email("joe.bloggs@example.com")
        .build();

    private static final SubscriptionRequest REQUEST = SubscriptionRequest.builder()
        .apiShortCode("pcd")
        .api("Test API")
        .environment("SBOX")
        .expectedVolume("LOW")
        .useCase("Test use case")
        .oauth2Capable(true)
        .declaration("I agree")
        .build();

    @Test
    void to_entity_should_map_all_fields_from_request_and_user() {
        MarketplaceRequestEntity entity = mapper.toEntity(REQUEST, USER);

        assertThat(entity.getType()).isEqualTo("SUBSCRIPTION");
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getOrgName()).isEqualTo("Test Org");
        assertThat(entity.getUserName()).isEqualTo("Joe Bloggs");
        assertThat(entity.getUserEmail()).isEqualTo("joe.bloggs@example.com");
    }

    @Test
    void from_entity_should_map_all_fields_from_entity_and_request() {
        MarketplaceRequestEntity entity = MarketplaceRequestEntity.builder()
            .orgName("Test Org")
            .userName("Joe Bloggs")
            .userEmail("joe.bloggs@example.com")
            .status("PENDING")
            .build();

        SubscriptionResponse response = mapper.fromEntity(entity, REQUEST);

        assertThat(response.getOrgName()).isEqualTo("Test Org");
        assertThat(response.getUserName()).isEqualTo("Joe Bloggs");
        assertThat(response.getUserEmail()).isEqualTo("joe.bloggs@example.com");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getApiShortCode()).isEqualTo("pcd");
        assertThat(response.getApi()).isEqualTo("Test API");
        assertThat(response.getEnvironment()).isEqualTo("SBOX");
        assertThat(response.getExpectedVolume()).isEqualTo("LOW");
        assertThat(response.getUseCase()).isEqualTo("Test use case");
        assertThat(response.isOauth2Capable()).isTrue();
        assertThat(response.getDeclaration()).isEqualTo("I agree");
    }
}
