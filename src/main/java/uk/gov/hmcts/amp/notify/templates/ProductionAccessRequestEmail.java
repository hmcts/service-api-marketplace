package uk.gov.hmcts.amp.notify.templates;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.amp.domain.NotifyEmail;

import java.util.List;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public class ProductionAccessRequestEmail implements NotifyEmail {

    public static final String TEMPLATE_ID = "9a21b7c6-d108-4d63-81bd-0bd52a2f511a";

    private final String emailAddress;
    private final String consumerName;
    private final String applicationName;
    private final List<String> apis;
    private final String reference;

    @Override
    public String getTemplateId() {
        return TEMPLATE_ID;
    }

    @Override
    public Map<String, Object> getPersonalisation() {
        // api_list stays a List so Notify renders it as bullets.
        return Map.of("consumer_name", consumerName,
            "application_name", applicationName,
            "api_list", apis);
    }
}
