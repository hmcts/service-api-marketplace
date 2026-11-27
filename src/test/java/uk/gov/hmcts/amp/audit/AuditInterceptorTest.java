package uk.gov.hmcts.amp.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import uk.gov.hmcts.amp.services.ClockService;
import uk.gov.hmcts.amp.services.SignedInCaller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditInterceptorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-18T10:15:02Z");
    private static final String ACTOR_OID = "9b1c4e2a-0000-0000-0000-000000000001";
    private static final String SIGNED_IN_OID = "aaaaaaaa-0000-0000-0000-000000000002";

    @Mock
    private SignedInCaller caller;

    @Spy
    private ClockService clockService = new ClockService(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    private AuditInterceptor interceptor;

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/apim/subscription-keys");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditInterceptor.AUDIT_LOGGER);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureAuditLines() {
        interceptor = new AuditInterceptor(new AuditWriter(clockService), caller);
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        auditLogger.detachAppender(appender);
    }

    @Test
    void afterCompletion_should_write_a_success_line_with_the_captured_details_and_requesting_user()
        throws Exception {
        when(caller.objectIdOrNull()).thenReturn(ACTOR_OID);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/apim/subscription-keys");
        request.setAttribute(AuditAspect.AUDIT_DETAILS_ATTRIBUTE,
            new AuditDetails(null, "amp-user003-acme-case-viewer-slc", "slc"));
        response.setStatus(201);

        interceptor.afterCompletion(request, response, handler("audited"), null);

        assertThat(appender.list).singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
            .containsEntry("audit_action", "CONSUMER_SUBSCRIPTION_KEY_CREATED")
            .containsEntry("audit_target", "amp-user003-acme-case-viewer-slc")
            .containsEntry("audit_outcome", "SUCCESS")
            .containsEntry("audit_status", "201")
            .containsEntry("audit_actor_oid", ACTOR_OID)
            .containsEntry("audit_endpoint", "POST /apim/subscription-keys")
            .containsEntry("audit_api", "slc")
            .containsEntry("audit_occurred_at", "2026-11-18T10:15:02Z"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void afterCompletion_should_prefer_the_captured_actor_whenTheEndpointNamesOne() throws Exception {
        when(caller.objectIdOrNull()).thenReturn(ACTOR_OID);
        request.setAttribute(AuditAspect.AUDIT_DETAILS_ATTRIBUTE, new AuditDetails(SIGNED_IN_OID, null, null));

        interceptor.afterCompletion(request, response, handler("audited"), null);

        assertThat(appender.list).singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
            .containsEntry("audit_actor_oid", SIGNED_IN_OID));
    }

    @Test
    void afterCompletion_should_write_a_failure_line_without_missing_fields_whenTheControllerMethodNeverRan()
        throws Exception {
        response.setStatus(400);

        interceptor.afterCompletion(request, response, handler("audited"), null);

        assertThat(appender.list).singleElement().satisfies(line -> assertThat(line.getMDCPropertyMap())
            .containsEntry("audit_outcome", "FAILURE")
            .containsEntry("audit_status", "400")
            .doesNotContainKeys("audit_target", "audit_api", "audit_actor_oid"));
    }

    @Test
    void afterCompletion_should_not_write_a_line_whenTheHandlerIsNotAudited() throws Exception {
        interceptor.afterCompletion(request, response, handler("notAudited"), null);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void afterCompletion_should_not_fail_the_request_whenTheAuditWriteFails() throws Exception {
        doThrow(new IllegalStateException("clock unavailable")).when(clockService).now();
        final HandlerMethod audited = handler("audited");

        assertThatNoException().isThrownBy(() -> interceptor.afterCompletion(request, response, audited, null));
    }

    private static HandlerMethod handler(final String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new AuditedEndpoints(), AuditedEndpoints.class.getMethod(methodName));
    }

    static class AuditedEndpoints {

        @LogAudit(action = AuditAction.CONSUMER_SUBSCRIPTION_KEY_CREATED)
        public void audited() {
            // Only the annotation matters: the interceptor reads it from the handler method.
        }

        public void notAudited() {
            // Only the missing annotation matters: the interceptor must ignore this handler.
        }
    }
}
