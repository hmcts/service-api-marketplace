package uk.gov.hmcts.amp.domain;

public final class ConsumerNames {

    private static final String PREFIX = "amp-consumer-";

    private ConsumerNames() {
    }

    public static String appRegistration(final String appname) {
        return PREFIX + appname;
    }

    public static String subscription(final String appname, final String shortCode) {
        return PREFIX + appname + "-" + shortCode;
    }
}
