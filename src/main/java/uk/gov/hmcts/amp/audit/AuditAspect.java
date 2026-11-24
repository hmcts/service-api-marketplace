package uk.gov.hmcts.amp.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Slf4j
@Aspect
@Component
public class AuditAspect {

    public static final String AUDIT_DETAILS_ATTRIBUTE = AuditAspect.class.getName() + ".details";

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    @Around("@annotation(logAudit)")
    public Object capture(final ProceedingJoinPoint joinPoint, final LogAudit logAudit) throws Throwable {
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            storeDetails(joinPoint, logAudit, result);
        }
    }

    private void storeDetails(final ProceedingJoinPoint joinPoint, final LogAudit logAudit, final Object result) {
        // Runs in a finally block on the request thread, so a bad expression must never replace the user's response.
        try {
            final EvaluationContext context = new MethodBasedEvaluationContext(joinPoint.getTarget(),
                ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs(), parameterNames);
            context.setVariable("result", result);
            final AuditDetails details = new AuditDetails(evaluate(logAudit.actor(), context),
                evaluate(logAudit.target(), context), evaluate(logAudit.api(), context));
            RequestContextHolder.currentRequestAttributes()
                .setAttribute(AUDIT_DETAILS_ATTRIBUTE, details, RequestAttributes.SCOPE_REQUEST);
        } catch (RuntimeException e) {
            log.error("Could not capture audit details for {}", logAudit.action(), e);
        }
    }

    private String evaluate(final String expression, final EvaluationContext context) {
        if (expression.isBlank()) {
            return null;
        }
        final Object value = parser.parseExpression(expression).getValue(context);
        return value == null ? null : value.toString();
    }
}
