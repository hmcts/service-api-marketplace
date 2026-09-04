package uk.gov.hmcts.cp.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import uk.gov.hmcts.cp.domain.PublishRequest;
import uk.gov.hmcts.cp.domain.PublishResponse;
import uk.gov.hmcts.cp.entity.OrganisationEntity;
import uk.gov.hmcts.cp.entity.PublishRequestEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.PublishMapperImpl;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PublishMapperTest {

    @InjectMocks
    private PublishMapperImpl mapper;

    private final UserEntity user = UserEntity.builder()
        .id(1)
        .organisation(OrganisationEntity.builder().name("Test Org").build())
        .firstName("Joe")
        .lastName("Bloggs")
        .email("joe.bloggs@example.com")
        .build();

    private final PublishRequest request = PublishRequest.builder()
        .apiName("Court Schedule")
        .owningTeam("Scheduling and Listing")
        .contactEmail("sandl-api@justice.gov.uk")
        .specUrl("https://raw.githubusercontent.com/hmcts/api-cp-crime-slc/main/openapi-spec.yml")
        .build();

    private PublishRequestEntity mapRequest() {
        return mapper.toEntity(
            user.getId(), "Test Org", user.getEmail(), user.getFirstName(), user.getLastName(), request);
    }

    @Test
    void to_entity_should_map_every_field_the_form_asks_for() {
        PublishRequestEntity entity = mapRequest();

        assertThat(entity.getApiName()).isEqualTo("Court Schedule");
        assertThat(entity.getOwningTeam()).isEqualTo("Scheduling and Listing");
        assertThat(entity.getContactEmail()).isEqualTo("sandl-api@justice.gov.uk");
        assertThat(entity.getSpecUrl())
            .isEqualTo("https://raw.githubusercontent.com/hmcts/api-cp-crime-slc/main/openapi-spec.yml");
    }

    @Test
    void to_entity_should_stamp_the_requester_from_the_user_and_not_the_request() {
        PublishRequestEntity entity = mapRequest();

        assertThat(entity.getOrgName()).isEqualTo("Test Org");
        assertThat(entity.getUserName()).isEqualTo("Joe Bloggs");
        assertThat(entity.getUserEmail()).isEqualTo("joe.bloggs@example.com");
    }

    @Test
    void to_entity_should_open_a_request_as_new() {
        assertThat(mapRequest().getStatus()).isEqualTo("NEW");
    }

    @Test
    void to_entity_should_leave_only_the_fields_someone_else_owns_unset() {
        // The strong assertion: any column added to the entity and left unmapped fails
        // here, rather than quietly persisting null. The id is the database's and
        // submittedAt is stamped by the service from ClockService, so both are the
        // mapper's to leave alone.
        assertThat(mapRequest()).hasNoNullFieldsOrPropertiesExcept("id", "reference", "submittedAt");
    }

    @Test
    void from_entity_should_populate_every_field_of_the_response() {
        PublishRequestEntity entity = mapRequest().toBuilder()
            .id(UUID.randomUUID())
            .reference("PR-2026-ABC123")
            .submittedAt(LocalDateTime.now())
            .build();

        PublishResponse response = mapper.fromEntity(user, entity);

        // Same guarantee in the other direction: a new response field left unmapped fails.
        assertThat(response).hasNoNullFieldsOrProperties();
    }

    @Test
    void from_entity_should_carry_the_stored_values_back_out() {
        PublishRequestEntity entity = mapRequest().toBuilder().reference("PR-2026-ABC123").build();

        PublishResponse response = mapper.fromEntity(user, entity);

        assertThat(response.getReference()).isEqualTo("PR-2026-ABC123");
        assertThat(response.getStatus()).isEqualTo("NEW");
        assertThat(response.getApiName()).isEqualTo("Court Schedule");
        assertThat(response.getOwningTeam()).isEqualTo("Scheduling and Listing");
        assertThat(response.getContactEmail()).isEqualTo("sandl-api@justice.gov.uk");
        assertThat(response.getSpecUrl())
            .isEqualTo("https://raw.githubusercontent.com/hmcts/api-cp-crime-slc/main/openapi-spec.yml");
    }

    @Test
    void from_entity_should_describe_the_requester_from_the_user_not_the_stored_row() {
        // The row carries its own copy of the requester, stamped at submission. The
        // response is built from the user record, so a renamed user reads correctly.
        PublishRequestEntity entity = mapRequest().toBuilder()
            .orgName("Stale Org")
            .userName("Stale Name")
            .userEmail("stale@example.com")
            .build();

        PublishResponse response = mapper.fromEntity(user, entity);

        assertThat(response.getRequestingOrgName()).isEqualTo("Test Org");
        assertThat(response.getRequestingUserName()).isEqualTo("Joe Bloggs");
        assertThat(response.getRequestingUserEmail()).isEqualTo("joe.bloggs@example.com");
    }

    @Test
    void to_entity_should_return_null_when_it_is_given_nothing() {
        assertThat(mapper.toEntity(0, null, null, null, null, null)).isNull();
    }
}
