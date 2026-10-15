package uk.gov.hmcts.amp.domain;

import jakarta.validation.constraints.NotBlank;

public record ApiAccess(@NotBlank String role) {
}
