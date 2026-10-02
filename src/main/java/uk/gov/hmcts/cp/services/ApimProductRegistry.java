package uk.gov.hmcts.cp.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the short codes shown on the registration form (e.g. SLC, RCC, DL, PCR, HRDS, PCD) to
 * the APIM Product id backing each one on {@code sps-api-mgmt-sbox}. Kept as one configured
 * value rather than an enum: the set of onboardable APIs changes independently of a code
 * deploy, and this repo has no other admin surface yet for managing it.
 *
 * <p>Format: {@code APIM_PRODUCT_MAP=SLC:product-id,RCC:product-id,...}.
 */
@Slf4j
@Service
public class ApimProductRegistry {

    @Value("${APIM_PRODUCT_MAP:}")
    private String productMapConfig;

    private Map<String, String> productIdsByShortCode;

    private Map<String, String> productIds() {
        if (productIdsByShortCode == null) {
            productIdsByShortCode = parse(productMapConfig);
        }
        return productIdsByShortCode;
    }

    private Map<String, String> parse(final String config) {
        Map<String, String> parsed = new HashMap<>();
        if (config == null || config.isBlank()) {
            return parsed;
        }
        for (String entry : config.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                parsed.put(parts[0].trim().toUpperCase(Locale.ROOT), parts[1].trim());
            } else {
                log.warn("Ignoring malformed APIM_PRODUCT_MAP entry: '{}'", entry);
            }
        }
        return parsed;
    }

    public String productIdFor(final String apiShortCode) {
        String productId = productIds().get(apiShortCode.toUpperCase(Locale.ROOT));
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown apiShortCode: " + apiShortCode);
        }
        return productId;
    }
}
