package uk.gov.hmcts.amp.audit;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.monitor.ingestion.LogsIngestionClient;
import com.azure.monitor.ingestion.LogsIngestionClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@EnableConfigurationProperties(AuditIngestionProperties.class)
@ConditionalOnProperty(name = "audit.ingestion.enabled", havingValue = "true")
public class AuditIngestionConfig implements DisposableBean {

    private static final int QUEUE_SIZE = 1024;
    private static final int MAX_FLUSH_MILLIS = 10_000;

    private final Logger auditLogger;
    private final AsyncAppender async;

    @Autowired
    public AuditIngestionConfig(final AuditIngestionProperties properties) {
        this(properties, uploaderFor(properties));
    }

    AuditIngestionConfig(final AuditIngestionProperties properties, final AuditUploader uploader) {
        if (!properties.configured()) {
            throw new IllegalStateException(
                "audit.ingestion.enabled is true but AUDIT_INGESTION_ENDPOINT or AUDIT_INGESTION_RULE_ID is not set");
        }
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final AuditIngestionAppender ingestion = new AuditIngestionAppender(uploader, properties.environment());
        ingestion.setContext(context);
        ingestion.setName("AUDIT_INGESTION");
        ingestion.start();

        async = new AsyncAppender();
        async.setContext(context);
        async.setName("AUDIT_ASYNC");
        async.setQueueSize(QUEUE_SIZE);
        // Never drop an audit entry: wait instead when the queue is full.
        async.setDiscardingThreshold(0);
        async.setNeverBlock(false);
        async.setMaxFlushTime(MAX_FLUSH_MILLIS);
        async.addAppender(ingestion);
        async.start();

        auditLogger = context.getLogger(AuditInterceptor.AUDIT_LOGGER);
        auditLogger.addAppender(async);
        auditLogger.setAdditive(false);
        log.info("Audit entries go to AmpAudit_CL through {}", properties.endpoint());
    }

    private static AuditUploader uploaderFor(final AuditIngestionProperties properties) {
        if (!properties.configured()) {
            return rows -> { };
        }
        final LogsIngestionClient client = new LogsIngestionClientBuilder()
            .endpoint(properties.endpoint())
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
        return rows -> client.upload(properties.ruleId(), AuditIngestionProperties.STREAM, List.copyOf(rows));
    }

    @Override
    public void destroy() {
        auditLogger.setAdditive(true);
        auditLogger.detachAppender(async);
        async.stop();
    }
}
