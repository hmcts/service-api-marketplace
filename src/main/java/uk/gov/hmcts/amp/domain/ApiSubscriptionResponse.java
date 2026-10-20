package uk.gov.hmcts.amp.domain;

/** The subscription key appears here and nowhere else; reading it back is a call to APIM. */
public record ApiSubscriptionResponse(String shortCode, String title, String productId,
                                      String subscriptionName, String subscriptionKey) {
}
