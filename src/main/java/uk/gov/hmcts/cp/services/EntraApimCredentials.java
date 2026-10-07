package uk.gov.hmcts.cp.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Where an application's credentials come from: the service itself ({@code APPLICATION_CREDENTIALS=local},
 * the default) or the real systems behind them ({@code entra}) - an Entra Client ID and Client Secret, and
 * one APIM Subscription Key for each API the application is connected to.
 *
 * <p>This only holds the switch and the calls; deciding what to do with the results, and in what order, is
 * {@link ApplicationManagementService}'s job. The methods named {@code undo...} are for clean-up after a
 * failure: they never throw, because the caller is already handling the real failure and a clean-up
 * failure must not hide it. Anything that could not be undone is logged with enough to do it by hand.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntraApimCredentials {

    static final String LOCAL = "local";
    static final String ENTRA = "entra";

    private final EntraAppRegistrationClient entra;
    private final ApimSubscriptionClient apim;
    private final ApimProductRegistry products;

    // Not final so tests can set it; the default keeps everything as it was before this existed.
    @Value("${APPLICATION_CREDENTIALS:local}")
    private String mode = LOCAL;

    /** A typo here would otherwise quietly leave the service issuing made-up credentials. */
    @PostConstruct
    void requireKnownMode() {
        String normalised = normalised();
        if (!LOCAL.equals(normalised) && !ENTRA.equals(normalised)) {
            throw new IllegalStateException("APPLICATION_CREDENTIALS must be 'local' or 'entra', not '" + mode + "'");
        }
    }

    public boolean enabled() {
        return ENTRA.equals(normalised());
    }

    public EntraAppRegistrationClient.Registration register(final String applicationName) {
        return entra.register(applicationName);
    }

    public EntraAppRegistrationClient.Secret addSecret(final String clientId) {
        return entra.addSecret(clientId);
    }

    public void revokeSecret(final String clientId, final String keyId) {
        entra.removeSecret(clientId, keyId);
    }

    /** The APIM Product behind an API. Asked for before anything is created, so an unknown API costs nothing. */
    public String productFor(final String apiId) {
        return products.productIdFor(apiId);
    }

    public ApimSubscriptionClient.Subscription subscribe(final String applicationName, final String productId) {
        return apim.createSubscription(applicationName, productId);
    }

    public void unsubscribe(final String subscriptionName) {
        apim.deleteSubscription(subscriptionName);
    }

    public void deleteApplication(final String clientId) {
        entra.delete(clientId);
    }

    public void undoRegistration(final String clientId) {
        try {
            entra.delete(clientId);
        } catch (RuntimeException failure) {
            log.error("Could not delete Entra application {} after a failure - remove it manually", clientId, failure);
        }
    }

    public void undoSecret(final String clientId, final String keyId) {
        try {
            entra.removeSecret(clientId, keyId);
        } catch (RuntimeException failure) {
            log.error("Could not remove client secret {} from Entra application {} after a failure - "
                + "remove it manually", keyId, clientId, failure);
        }
    }

    public void undoSubscription(final String subscriptionName) {
        try {
            apim.deleteSubscription(subscriptionName);
        } catch (RuntimeException failure) {
            log.error("Could not delete APIM subscription {} after a failure - remove it manually",
                subscriptionName, failure);
        }
    }

    private String normalised() {
        return mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
    }
}
