package uk.gov.hmcts.amp.domain;

/** How many calls a day a consumer expects to make once live. */
public enum CallVolume implements Answer {

    LOW("low", "Low (under 1,000 calls per day)"),
    MEDIUM("medium", "Medium (1,000 to 100,000 calls per day)"),
    HIGH("high", "High (over 100,000 calls per day)");

    private final String slug;
    private final String label;

    CallVolume(final String slug, final String label) {
        this.slug = slug;
        this.label = label;
    }

    public static CallVolume fromSlug(final String slug) {
        return Answer.fromSlug(values(), slug, CallVolume.class);
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
