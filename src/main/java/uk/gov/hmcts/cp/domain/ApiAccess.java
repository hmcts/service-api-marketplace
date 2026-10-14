package uk.gov.hmcts.cp.domain;

import jakarta.validation.constraints.NotBlank;

public record ApiAccess(@NotBlank String role) {
}
