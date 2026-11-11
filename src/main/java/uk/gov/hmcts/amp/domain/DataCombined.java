package uk.gov.hmcts.amp.domain;

/** Whether the API data will be combined or linked with other data. */
public enum DataCombined implements Answer {

    NO("no", "No"),
    YES("yes", "Yes"),
    UNSURE("unsure", "Unsure");

    private final String slug;
    private final String label;

    DataCombined(final String slug, final String label) {
        this.slug = slug;
        this.label = label;
    }

    public static DataCombined fromSlug(final String slug) {
        return Answer.fromSlug(values(), slug, DataCombined.class);
    }

    /** Unsure is treated as yes: the team needs to know what it might be combined with either way. */
    public boolean needsDescribing() {
        return this == YES || this == UNSURE;
    }

    @Override
    public String slug() {
        return slug;
    }

    @Override
    public String label() {
        return label;
    }
}
