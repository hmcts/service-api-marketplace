package uk.gov.hmcts.cp.domain;

import lombok.Getter;

@Getter
public enum RequestType {
    SUBSCRIPTION("AR"),
    PUBLISH("PR");

    private final String referencePrefix;

    RequestType(final String referencePrefix) {
        this.referencePrefix = referencePrefix;
    }
}
