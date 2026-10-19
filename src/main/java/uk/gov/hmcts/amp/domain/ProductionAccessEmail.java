package uk.gov.hmcts.amp.domain;

import java.util.List;

/**
 * The fields the Notify template asks for. api_list is a list rather than a joined string so
 * Notify renders it as bullets; the wording around it lives in the template, not here.
 */
public record ProductionAccessEmail(String emailAddress, String consumerName, String applicationName,
                                    List<String> apis, String reference) {
}
