package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.ApiKeySummary;
import uk.gov.hmcts.cp.domain.ApplicationView;
import uk.gov.hmcts.cp.domain.ConnectedApi;
import uk.gov.hmcts.cp.domain.OwnerRef;
import uk.gov.hmcts.cp.domain.TeamMemberView;
import uk.gov.hmcts.cp.domain.ViewerRole;
import uk.gov.hmcts.cp.entity.ApplicationEntity;
import uk.gov.hmcts.cp.entity.ApplicationSecretEntity;
import uk.gov.hmcts.cp.entity.ApplicationTeamMemberEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns stored rows into what the frontend reads. Timestamps are held as UTC without a zone, so
 * they are returned as ISO instants ending in Z - without it a browser would read them as local time.
 * The two JSON-as-text columns are decoded and encoded here and nowhere else.
 */
@Slf4j
@Service
public class ApplicationViewFactory {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApplicationView view(final ApplicationEntity application, final ViewerRole role) {
        return new ApplicationView(
            application.getPublicId().toString(),
            application.getClientId(),
            application.getName(),
            application.getDescription(),
            application.getEnvironment(),
            new OwnerRef("user", application.getUser().getId()),
            application.getPublicKeyUrl(),
            application.getCallbackUrl(),
            attributes(application.getCustomAttributes()),
            connectedApis(application.getConnectedApis()),
            instant(application.getCreatedAt()),
            role.json());
    }

    public ApiKeySummary summary(final ApplicationSecretEntity secret) {
        return new ApiKeySummary(secret.getPublicId().toString(), secret.getKeyPreview(),
            instant(secret.getCreatedAt()), instant(secret.getRevokedAt()));
    }

    public TeamMemberView teamMember(final ApplicationTeamMemberEntity member) {
        return new TeamMemberView(member.getPublicId().toString(), member.getEmail(), member.getRole(),
            instant(member.getAddedAt()));
    }

    public Map<String, String> attributes(final String json) {
        return read(json, new TypeReference<LinkedHashMap<String, String>>() { }, new LinkedHashMap<>());
    }

    public List<ConnectedApi> connectedApis(final String json) {
        return read(json, new TypeReference<List<ConnectedApi>>() { }, List.of());
    }

    public String write(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode application data", e);
        }
    }

    // A stored value that no longer parses must not take the whole list down with it: say so in the
    // log, where someone can fix the row, and show the application with that field empty.
    private <T> T read(final String json, final TypeReference<T> type, final T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Stored application data is not valid JSON and was ignored", e);
            return fallback;
        }
    }

    private String instant(final LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC).toString();
    }
}
