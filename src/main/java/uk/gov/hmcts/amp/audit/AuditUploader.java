package uk.gov.hmcts.amp.audit;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface AuditUploader {

    void upload(List<Map<String, Object>> rows);
}
