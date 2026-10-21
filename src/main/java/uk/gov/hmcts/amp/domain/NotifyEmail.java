package uk.gov.hmcts.amp.domain;

import java.util.Map;

/** An email Notify can send: which template, to whom, and what to fill it with. */
public interface NotifyEmail {

    String getTemplateId();

    String getEmailAddress();

    String getReference();

    Map<String, Object> getPersonalisation();
}
