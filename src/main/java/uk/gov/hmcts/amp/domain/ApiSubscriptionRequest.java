package uk.gov.hmcts.amp.domain;

import jakarta.validation.constraints.NotBlank;

public record ApiSubscriptionRequest(
    @NotBlank(message = "Say which application the subscription is for.")
    String clientId,

    @NotBlank(message = "Choose which API to subscribe to.")
    String shortCode) {
}
