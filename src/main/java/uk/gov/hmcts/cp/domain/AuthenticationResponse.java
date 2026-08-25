package uk.gov.hmcts.cp.domain;

public record AuthenticationResponse(User user, String token) {
}
