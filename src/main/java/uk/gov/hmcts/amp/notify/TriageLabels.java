package uk.gov.hmcts.amp.notify;

import java.util.List;
import java.util.Map;

/**
 * The wording the Data Access Team read. A request stores each triage answer as the slug the
 * form posts, which survives the question being reworded; this is the one place that has to
 * read as English. A slug with no label here is passed through rather than dropped, so an
 * answer added to the form before it is added here still reaches the reviewer.
 */
public final class TriageLabels {

    private static final Map<String, String> ORGANISATION_TYPES = Map.of(
        "hmcts", "HMCTS",
        "moj", "Ministry of Justice",
        "other-government", "Other government department/public body",
        "external", "Other external organisation");

    private static final Map<String, String> DATA_RECIPIENTS = Map.of(
        "own-team", "Me/my immediate team only",
        "other-hmcts", "Other HMCTS teams",
        "wider-moj", "Wider MOJ",
        "other-government", "Another government department/public body",
        "supplier", "Supplier/contract/delivery partner",
        "external", "Other external organisation",
        "public", "The data will be published or used in a public-facing service");

    private static final Map<String, String> DATA_COMBINED = Map.of(
        "no", "No",
        "yes", "Yes",
        "unsure", "Unsure");

    private TriageLabels() {
    }

    public static String organisationType(final String slug) {
        return label(ORGANISATION_TYPES, slug);
    }

    public static String dataCombined(final String slug) {
        return label(DATA_COMBINED, slug);
    }

    /** A list rather than a joined String, so Notify renders the answers as bullets. */
    public static List<String> dataRecipients(final List<String> slugs) {
        return slugs == null ? List.of() : slugs.stream().map(slug -> label(DATA_RECIPIENTS, slug)).toList();
    }

    private static String label(final Map<String, String> labels, final String slug) {
        return slug == null ? "" : labels.getOrDefault(slug, slug);
    }
}
