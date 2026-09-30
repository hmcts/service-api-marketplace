package uk.gov.hmcts.cp.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.ApiCredential;
import uk.gov.hmcts.cp.domain.ApplicationResponse;
import uk.gov.hmcts.cp.entity.ApplicationApiKeyEntity;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.mappers.ApplicationMapperImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ApplicationMapperTest {

    @InjectMocks
    private ApplicationMapperImpl mapper;

    private final ApplicationEntity application = ApplicationEntity.builder()
        .id(1L)
        .name("Test App")
        .environment("sandbox")
        .clientId("11111111-1111-1111-1111-111111111111")
        .createdAt(LocalDateTime.now())
        .build();

    private final ApplicationApiKeyEntity apiKey = ApplicationApiKeyEntity.builder()
        .id(2L)
        .apiShortCode("PCD")
        .publisherId("product-pcd-sandbox")
        .subscriptionKey("a-subscription-key")
        .createdAt(LocalDateTime.now())
        .build();

    @Test
    void from_entity_should_map_all_fields() {
        ApiCredential credential = mapper.fromEntity(apiKey);

        assertThat(credential.getApiShortCode()).isEqualTo("PCD");
        assertThat(credential.getPublisherId()).isEqualTo("product-pcd-sandbox");
        assertThat(credential.getSubscriptionKey()).isEqualTo("a-subscription-key");
    }

    @Test
    void to_response_should_map_the_application_and_its_api_keys() {
        ApplicationResponse response = mapper.toResponse(application, List.of(apiKey), "a-client-secret");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Test App");
        assertThat(response.getEnvironment()).isEqualTo("sandbox");
        assertThat(response.getClientId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(response.getClientSecret()).isEqualTo("a-client-secret");
        assertThat(response.getApiCredentials()).hasSize(1);
        assertThat(response.getApiCredentials().get(0).getApiShortCode()).isEqualTo("PCD");
    }

    @Test
    void to_response_should_allow_a_null_client_secret_for_applications_read_back_later() {
        ApplicationResponse response = mapper.toResponse(application, List.of(apiKey), null);

        assertThat(response.getClientSecret()).isNull();
    }
}
