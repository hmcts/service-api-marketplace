package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A product a subscription key can be created against. productId is what createKey takes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApimProduct(String productId, String displayName, String description, String state) {
}
