package uk.gov.hmcts.cp.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.LocalDateTime;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.SubscriptionRequest;
import uk.gov.hmcts.cp.domain.SubscriptionResponse;
import uk.gov.hmcts.cp.entity.SubscriptionRequestEntity;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapperImpl;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SubscriptionMapperTest {

    @InjectMocks
    private SubscriptionMapperImpl mapper;

    private final UserEntity user = UserEntity.builder()
        .id(1)
        .organisation(OrganisationEntity.builder().name("Test Org").build())
        .firstName("Joe")
        .lastName("Bloggs")
        .email("joe.bloggs@example.com")
        .build();

    private final SubscriptionRequest request = SubscriptionRequest.builder()
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
        SubscriptionRequestEntity entity = mapper.toEntity(
            user.getId(),
            "Test Org",
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            request
        );

        assertThat(entity.getType()).isEqualTo("SUBSCRIPTION");
        assertThat(entity.getStatus()).isEqualTo("NEW");
        assertThat(entity.getOrgName()).isEqualTo("Test Org");
        assertThat(entity.getUserName()).isEqualTo("Joe Bloggs");
        assertThat(entity.getUserEmail()).isEqualTo("joe.bloggs@example.com");
        assertThat(entity.getApiShortCode()).isEqualTo("pcd");
        assertThat(entity.getApi()).isEqualTo("Test API");
        assertThat(entity.getEnvironment()).isEqualTo("SBOX");
        assertThat(entity.getExpectedVolume()).isEqualTo("LOW");
        assertThat(entity.getUseCase()).isEqualTo("Test use case");
        assertThat(entity.isOauth2Capable()).isTrue();
        assertThat(entity.getDeclaration()).isEqualTo("I agree");
    }

    @Test
    void from_entity_should_map_all_fields_from_entity_and_user() {
        SubscriptionRequestEntity entity = SubscriptionRequestEntity.builder()
            .status("NEW")
            .apiShortCode("pcd")
            .api("Test API")
            .environment("SBOX")
            .expectedVolume("LOW")
            .useCase("Test use case")
            .oauth2Capable(true)
            .declaration("I agree")
            .build();

        SubscriptionResponse response = mapper.fromEntity(user, entity);

        assertThat(response.getRequestingOrgName()).isEqualTo("Test Org");
        assertThat(response.getRequestingUserName()).isEqualTo("Joe Bloggs");
        assertThat(response.getRequestingUserEmail()).isEqualTo("joe.bloggs@example.com");
        assertThat(response.getStatus()).isEqualTo("NEW");
        assertThat(response.getApiShortCode()).isEqualTo("pcd");
        assertThat(response.getApi()).isEqualTo("Test API");
        assertThat(response.getEnvironment()).isEqualTo("SBOX");
        assertThat(response.getExpectedVolume()).isEqualTo("LOW");
        assertThat(response.getUseCase()).isEqualTo("Test use case");
        assertThat(response.isOauth2Capable()).isTrue();
        assertThat(response.getDeclaration()).isEqualTo("I agree");
    }

    private SubscriptionRequestEntity mapRequest() {
        return mapper.toEntity(
            user.getId(), "Test Org", user.getEmail(), user.getFirstName(), user.getLastName(), request);
    }

    @Test
    void to_entity_should_stamp_the_requester_from_the_user_and_not_the_request() {
        SubscriptionRequestEntity entity = mapRequest();

        assertThat(entity.getOrgName()).isEqualTo("Test Org");
        assertThat(entity.getUserName()).isEqualTo("Joe Bloggs");
        assertThat(entity.getUserEmail()).isEqualTo("joe.bloggs@example.com");
    }

    @Test
    void to_entity_should_leave_only_the_fields_someone_else_owns_unset() {
        // The strong assertion: any column added to the entity and left unmapped fails
        // here, rather than quietly persisting null. The id is the database's and
        // submittedAt is stamped by the service from ClockService, so both are the
        // mapper's to leave alone.
        assertThat(mapRequest()).hasNoNullFieldsOrPropertiesExcept("id", "submittedAt");
    }

    @Test
    void from_entity_should_populate_every_field_of_the_response() {
        SubscriptionRequestEntity entity = mapRequest().toBuilder()
            .id(UUID.randomUUID())
            .submittedAt(LocalDateTime.now())
            .build();

        SubscriptionResponse response = mapper.fromEntity(user, entity);

        // Same guarantee in the other direction: a new response field left unmapped fails.
        assertThat(response).hasNoNullFieldsOrProperties();
    }

    @Test
    void from_entity_should_describe_the_requester_from_the_user_not_the_stored_row() {
        // The row carries its own copy of the requester, stamped at submission. The
        // response is built from the user record, so a renamed user reads correctly.
        SubscriptionRequestEntity entity = mapRequest().toBuilder()
            .orgName("Stale Org")
            .userName("Stale Name")
            .userEmail("stale@example.com")
            .build();

        SubscriptionResponse response = mapper.fromEntity(user, entity);

        assertThat(response.getRequestingOrgName()).isEqualTo("Test Org");
        assertThat(response.getRequestingUserName()).isEqualTo("Joe Bloggs");
        assertThat(response.getRequestingUserEmail()).isEqualTo("joe.bloggs@example.com");
    }

    @Test
    void to_entity_should_return_null_when_it_is_given_nothing() {
        assertThat(mapper.toEntity(0, null, null, null, null, null)).isNull();
    }
}
