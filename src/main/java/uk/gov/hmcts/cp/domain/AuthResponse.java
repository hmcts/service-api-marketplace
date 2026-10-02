package uk.gov.hmcts.cp.domain;

/** Returned by register and login: the account, and the bearer token that signs the caller in. */
public record AuthResponse(AccountResponse user, String token) {
}
