package uk.gov.hmcts.amp.domain;

import java.util.Arrays;

/**
 * A form answer that is stored as a slug and read by a person as a label.
 *
 * <p>Holding both on one constant is the point: the slug survives the question being reworded,
 * and nobody can add an answer without also deciding what it says in a reviewer's email.
 */
public interface Answer {

    String slug();

    String label();

    /**
     * An unknown slug is an error rather than something to pass through: every answer is validated
     * against the same enum on the way in, so a stored value is one of these by construction. A
     * slug nobody recognises means the data is wrong, and saying so beats showing a blank.
     */
    static <E extends Enum<E> & Answer> E fromSlug(final E[] values, final String slug, final Class<E> type) {
        if (slug == null) {
            return null;
        }
        return Arrays.stream(values)
            .filter(value -> slug.equals(value.slug()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "%s has no value for '%s'.".formatted(type.getSimpleName(), slug)));
    }
}
