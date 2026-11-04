package uk.gov.hmcts.amp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an API's published OpenAPI document from the developer portal. The portal serves it to
 * anyone, where the same text through ARM costs an Azure identity and a role on the instance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperPortalClient {

    private static final String API_VERSION = "2022-04-01-preview";
    // The link comes back with the filename in rscd quoted but not escaped, and a quote is
    // not legal in a query. curl tolerates it; URI.create does not.
    private static final Pattern ILLEGAL_IN_QUERY = Pattern.compile("[\"<>\\\\^`{|}\\s]");

    private final RestClient developerPortal;

    /**
     * Null where the specification carries no description, which is a specification nobody has
     * written text into yet. Throws only where the specification itself could not be fetched,
     * because that is the portal or the registration being wrong rather than merely empty.
     */
    public String description(final String apimApiName) {
        if (apimApiName == null || apimApiName.isBlank()) {
            throw unavailable(apimApiName, "it has no API Management name recorded", null);
        }
        return descriptionIn(specification(apimApiName)).orElse(null);
    }

    /**
     * Two calls: the portal answers with a short-lived link to the document in blob storage
     * rather than with the document itself.
     */
    private String specification(final String apimApiName) {
        try {
            final String response = developerPortal.get()
                .uri(builder -> builder.path("/developer/apis/{api}")
                    .queryParam("api-version", API_VERSION)
                    .queryParam("export", "true")
                    .queryParam("format", "openapi-link")
                    .build(apimApiName))
                .retrieve()
                .body(String.class);
            final String link = text(parse(response).get("link"))
                .orElseThrow(() -> unavailable(apimApiName,
                    "the developer portal published no specification for it", null));
            return developerPortal.get().uri(asUri(link)).retrieve().body(String.class);
        } catch (final ResponseStatusException alreadyExplained) {
            throw alreadyExplained;
        } catch (final RuntimeException notRead) {
            throw unavailable(apimApiName, "the developer portal could not be read", notRead);
        }
    }

    private URI asUri(final String link) {
        final Matcher illegal = ILLEGAL_IN_QUERY.matcher(link);
        final StringBuilder encoded = new StringBuilder();
        while (illegal.find()) {
            illegal.appendReplacement(encoded, String.format("%%%02X", (int) illegal.group().charAt(0)));
        }
        illegal.appendTail(encoded);
        return URI.create(encoded.toString());
    }

    private Optional<String> descriptionIn(final String specification) {
        final Object info = parse(specification).get("info");
        return info instanceof Map<?, ?> fields ? text(fields.get("description")) : Optional.empty();
    }

    /** YAML is a superset of JSON, so the link and the document it points at parse the same way. */
    private Map<?, ?> parse(final String document) {
        if (document == null || document.isBlank()) {
            return Map.of();
        }
        final Object parsed = new Yaml().load(document);
        return parsed instanceof Map<?, ?> fields ? fields : Map.of();
    }

    private Optional<String> text(final Object value) {
        return Optional.ofNullable(value).map(Object::toString).map(String::trim).filter(found -> !found.isEmpty());
    }

    private ResponseStatusException unavailable(final String apimApiName, final String because,
                                                final RuntimeException cause) {
        log.error("No description for {}: {}", apimApiName, because, cause);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "No description for " + apimApiName + ": " + because + ".");
    }
}
