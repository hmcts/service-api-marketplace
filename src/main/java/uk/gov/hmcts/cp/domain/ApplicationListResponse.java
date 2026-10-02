package uk.gov.hmcts.cp.domain;

import java.util.List;

public record ApplicationListResponse(List<ApplicationView> applications) {
}
