package uk.gov.hmcts.amp.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditAspectTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private Endpoints endpoints;

    @BeforeEach
    void proxyEndpointsWithinARequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        final AspectJProxyFactory factory = new AspectJProxyFactory(new Endpoints());
        factory.addAspect(new AuditAspect());
        endpoints = factory.getProxy();
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void capture_should_store_the_target_and_api_from_the_result_and_arguments() {
        endpoints.create("slc");

        assertThat(request.getAttribute(AuditAspect.AUDIT_DETAILS_ATTRIBUTE))
            .isEqualTo(new AuditDetails(null, "amp-user003-acme-slc", "slc"));
    }

    @Test
    void capture_should_store_the_details_and_rethrow_when_the_method_fails() {
        assertThatThrownBy(() -> endpoints.delete("client-1")).isInstanceOf(IllegalStateException.class);

        assertThat(request.getAttribute(AuditAspect.AUDIT_DETAILS_ATTRIBUTE))
            .isEqualTo(new AuditDetails(null, "client-1", null));
    }

    @Test
    void capture_should_return_the_result_and_store_nothing_when_an_expression_cannot_be_evaluated() {
        assertThat(endpoints.broken()).isEqualTo("done");

        assertThat(request.getAttribute(AuditAspect.AUDIT_DETAILS_ATTRIBUTE)).isNull();
    }

    static class Endpoints {

        @LogAudit(action = AuditAction.CONSUMER_SUBSCRIPTION_KEY_CREATED, target = "#result", api = "#shortCode")
        public String create(final String shortCode) {
            return "amp-user003-acme-" + shortCode;
        }

        @LogAudit(action = AuditAction.CONSUMER_APPLICATION_DELETED, target = "#clientId")
        public void delete(final String clientId) {
            throw new IllegalStateException("not found");
        }

        @LogAudit(action = AuditAction.CONSUMER_APPLICATION_DELETED, target = "#result.noSuchProperty")
        public String broken() {
            return "done";
        }
    }
}
