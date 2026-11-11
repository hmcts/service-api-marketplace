package uk.gov.hmcts.amp.domain;

/** Which kind of organisation is asking, which is the first thing a reviewer triages on. */
public enum OrganisationType implements Answer {

    HMCTS("hmcts", "HMCTS"),
    MOJ("moj", "Ministry of Justice"),
    OTHER_GOVERNMENT("other-government", "Other government department/public body"),
    EXTERNAL("external", "Other external organisation");

    private final String slug;
    private final String label;

    OrganisationType(final String slug, final String label) {
        this.slug = slug;
        this.label = label;
    }

    public static OrganisationType fromSlug(final String slug) {
        return Answer.fromSlug(values(), slug, OrganisationType.class);
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
