package uk.gov.hmcts.amp.domain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ProductionAccessRequest(
    @NotBlank(message = "Choose which application is going live.")
    String clientId,

    @NotBlank(message = "Enter the organisation's legal name.")
    @Size(max = 255, message = "Organisation must be 255 characters or fewer.")
    String organisation,

    @NotNull(message = "Enter a go live date.")
    LocalDate goLiveDate,

    @Pattern(regexp = "low|medium|high", message = "Call volume must be low, medium or high.")
    String callVolume,

    @NotBlank(message = "Describe the data request, how you plan to use it and why you need it.")
    @Size(max = 2000, message = "Data request must be 2000 characters or fewer.")
    String dataRequest,

    @Size(max = 2000, message = "Proposed outputs must be 2000 characters or fewer.")
    String proposedOutputs,

    @Pattern(regexp = "hmcts|moj|other-government|external",
        message = "Organisation type must be hmcts, moj, other-government or external.")
    String organisationType,

    List<@Pattern(regexp = "own-team|other-hmcts|wider-moj|other-government|supplier|external|public",
        message = "Data recipients must be from the published list.") String> dataRecipients,

    @Size(max = 2000, message = "Data recipients explanation must be 2000 characters or fewer.")
    String dataRecipientsExplanation,

    @Pattern(regexp = "no|yes|unsure", message = "Data combined must be no, yes or unsure.")
    String dataCombined,

    @Size(max = 2000, message = "Data combined description must be 2000 characters or fewer.")
    String dataCombinedDescription) {

    private static final String OWN_TEAM_ONLY = "own-team";

    /** Anything beyond the requester's own team is data leaving them, which has to be explained. */
    @AssertTrue(message = "Explain who will receive the data and why.")
    public boolean isDataRecipientsExplained() {
        final boolean leavesTheTeam = dataRecipients != null
            && dataRecipients.stream().anyMatch(recipient -> !OWN_TEAM_ONLY.equals(recipient));
        return !leavesTheTeam || (dataRecipientsExplanation != null && !dataRecipientsExplanation.isBlank());
    }

    /** Unsure is treated as yes: the team needs to know what it might be combined with either way. */
    @AssertTrue(message = "Describe the other data and what the combined data will be used for.")
    public boolean isDataCombinationDescribed() {
        final boolean combines = "yes".equals(dataCombined) || "unsure".equals(dataCombined);
        return !combines || (dataCombinedDescription != null && !dataCombinedDescription.isBlank());
    }
}
