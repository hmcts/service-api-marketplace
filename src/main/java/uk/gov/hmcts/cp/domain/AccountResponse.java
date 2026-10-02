package uk.gov.hmcts.cp.domain;

/** A marketplace user as exposed to clients. Never carries the password hash. */
public record AccountResponse(int id, String firstName, String lastName, String email, String role) {
}
