package uk.gov.hmcts.amp.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogAudit {

    AuditAction action();

    String target() default "";

    String api() default "";

    String actor() default "";
}
