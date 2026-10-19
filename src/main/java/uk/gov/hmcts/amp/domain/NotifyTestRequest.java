package uk.gov.hmcts.amp.domain;

import java.util.List;

/** Temporary. Every field is optional; whatever is left out falls back to an example value. */
public record NotifyTestRequest(String consumerName, String applicationName, List<String> apis,
                                String reference) {
}
