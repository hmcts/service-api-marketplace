package uk.gov.hmcts.cp.domain;

import java.util.UUID;

public record User(UUID id, String firstName, String lastName, String email, Role role) {
}
