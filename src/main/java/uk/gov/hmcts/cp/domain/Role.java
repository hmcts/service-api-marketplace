package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Role {

    CONSUMER,
    PRODUCER;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Role fromValue(final String value) {
        for (final Role role : values()) {
            if (role.value().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Role must be \"consumer\" or \"producer\".");
    }
}
