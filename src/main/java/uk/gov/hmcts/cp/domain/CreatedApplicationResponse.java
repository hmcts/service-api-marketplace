package uk.gov.hmcts.cp.domain;

/** The application, and its first client secret - the only time that secret is ever returned. */
public record CreatedApplicationResponse(ApplicationView application, String apiKey) {
}
