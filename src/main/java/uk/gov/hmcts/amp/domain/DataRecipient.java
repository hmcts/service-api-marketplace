package uk.gov.hmcts.amp.domain;

import java.util.List;

/** Who will have access to, or receive, the data. A request can name several at once. */
public enum DataRecipient implements Answer {

    OWN_TEAM("own-team", "Me/my immediate team only"),
    OTHER_HMCTS("other-hmcts", "Other HMCTS teams"),
    WIDER_MOJ("wider-moj", "Wider MOJ"),
    OTHER_GOVERNMENT("other-government", "Another government department/public body"),
    SUPPLIER("supplier", "Supplier/contract/delivery partner"),
    EXTERNAL("external", "Other external organisation"),
    PUBLIC("public", "The data will be published or used in a public-facing service");

    private final String slug;
    private final String label;

    DataRecipient(final String slug, final String label) {
        this.slug = slug;
        this.label = label;
    }

    public static DataRecipient fromSlug(final String slug) {
        return Answer.fromSlug(values(), slug, DataRecipient.class);
    }

    public static List<DataRecipient> fromSlugs(final List<String> slugs) {
        return slugs == null ? List.of() : slugs.stream().map(DataRecipient::fromSlug).toList();
    }

    /** Anything beyond the requester's own team is data leaving them, which has to be explained. */
    public static boolean leavesTheTeam(final List<DataRecipient> recipients) {
        return recipients != null && recipients.stream().anyMatch(recipient -> recipient != OWN_TEAM);
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
