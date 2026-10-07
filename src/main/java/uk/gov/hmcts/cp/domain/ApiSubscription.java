package uk.gov.hmcts.cp.domain;

/** The APIM Subscription Key an application was given for one API (the API's id as the frontend knows it). */
public record ApiSubscription(String apiId, String subscriptionKey) {
}
