package uk.gov.hmcts.amp.domain;

public record ApplicationAllowance(int maxPerUser, long count, boolean canCreate) {

    public static ApplicationAllowance of(final int maxPerUser, final long count) {
        return new ApplicationAllowance(maxPerUser, count, count < maxPerUser);
    }
}