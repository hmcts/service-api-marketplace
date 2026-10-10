package uk.gov.hmcts.cp.domain;

import java.util.List;
import java.util.Map;

/**
 * An application as the frontend sees it. The id is the public id, never the database key.
 */
public record ApplicationView(
    String id,
    String clientId,
    String name,
    String description,
    String environment,
    OwnerRef owner,
    String publicKeyUrl,
    String callbackUrl,
    Map<String, String> customAttributes,
    List<ConnectedApi> connectedApis,
    String createdAt,
    String viewerRole) {
}
