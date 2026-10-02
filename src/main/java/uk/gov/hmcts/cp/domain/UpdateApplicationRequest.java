package uk.gov.hmcts.cp.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Anything left out (null) is left as it was; custom attributes are merged into the existing ones. */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UpdateApplicationRequest {

    private String description;

    private String publicKeyUrl;

    private String callbackUrl;

    private Map<String, String> customAttributes;
}
