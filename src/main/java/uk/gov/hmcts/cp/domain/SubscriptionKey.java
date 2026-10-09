package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Key values are null unless Azure was asked for them: a list does not return them. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriptionKey(String name, String displayName, String scope, String productId,
                              String state, String primaryKey, String secondaryKey) {

    public static SubscriptionKey keysOnly(final String name, final String primary, final String secondary) {
        return new SubscriptionKey(name, null, null, null, null, primary, secondary);
    }

    public SubscriptionKey withKeys(final String primary, final String secondary) {
        return new SubscriptionKey(name, displayName, scope, productId, state, primary, secondary);
    }
}
