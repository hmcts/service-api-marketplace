package uk.gov.hmcts.amp.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.VariableReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every @LogAudit expression is only text to the compiler and is evaluated after the action has run, so a
 * mistake would only show as an audit line without its target. These tests read every expression in the
 * service and fail the build if one does not parse, names a variable the method does not have, or reads a
 * property or method its type does not have.
 */
class LogAuditExpressionsTest {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    static Stream<Arguments> auditedMethods() {
        final ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        return scanner.findCandidateComponents("uk.gov.hmcts.amp").stream()
            .map(bean -> ClassUtils.resolveClassName(bean.getBeanClassName(), null))
            .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
            .filter(method -> method.isAnnotationPresent(LogAudit.class))
            .map(method -> Arguments.of(method.getDeclaringClass().getSimpleName() + "." + method.getName(), method));
    }

    @Test
    void the_scan_should_find_the_audited_endpoints() {
        assertThat(auditedMethods()).hasSizeGreaterThanOrEqualTo(13);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("auditedMethods")
    void every_expression_should_parse_and_read_only_what_exists(final String name, final Method method) {
        final LogAudit audit = method.getAnnotation(LogAudit.class);

        for (final String expression : List.of(audit.target(), audit.api(), audit.actor())) {
            assertThat(problems(method, expression)).as("%s: %s", name, expression).isEmpty();
        }
    }

    @Test
    void the_check_should_accept_what_the_controllers_use() throws Exception {
        final Method method = Sample.class.getMethod("create", String.class, Request.class);

        assertThat(problems(method, "#clientId")).isEmpty();
        assertThat(problems(method, "#result?.name")).isEmpty();
        assertThat(problems(method, "#request.shortCode()")).isEmpty();
        assertThat(problems(method, "")).isEmpty();
    }

    @Test
    void the_check_should_catch_a_stray_character() throws Exception {
        final Method method = Sample.class.getMethod("create", String.class, Request.class);

        assertThat(problems(method, "#result?.name(")).singleElement().asString().contains("does not parse");
        assertThat(problems(method, "#result?.name)")).singleElement().asString().contains("does not parse");
    }

    @Test
    void the_check_should_catch_a_variable_the_method_does_not_have() throws Exception {
        final Method method = Sample.class.getMethod("create", String.class, Request.class);

        assertThat(problems(method, "#clientd")).singleElement().asString().contains("#clientd");
    }

    @Test
    void the_check_should_catch_a_property_or_method_the_type_does_not_have() throws Exception {
        final Method method = Sample.class.getMethod("create", String.class, Request.class);

        assertThat(problems(method, "#result?.nmae")).singleElement().asString().contains("nmae");
        assertThat(problems(method, "#request.shortCod()")).singleElement().asString().contains("shortCod");
    }

    static List<String> problems(final Method method, final String expression) {
        if (expression.isBlank()) {
            return List.of();
        }
        final SpelExpression parsed;
        try {
            parsed = (SpelExpression) PARSER.parseExpression(expression);
        } catch (ParseException e) {
            return List.of("does not parse: " + e.getSimpleMessage());
        }
        final Map<String, Type> variables = new HashMap<>();
        final String[] names = PARAMETER_NAMES.getParameterNames(method);
        final Type[] types = method.getGenericParameterTypes();
        for (int i = 0; names != null && i < names.length; i++) {
            variables.put(names[i], types[i]);
        }
        variables.put("result", method.getGenericReturnType());

        final List<String> problems = new ArrayList<>();
        check(parsed.getAST(), variables, problems);
        return problems;
    }

    private static void check(final SpelNode node, final Map<String, Type> variables, final List<String> problems) {
        if (node instanceof CompoundExpression) {
            Class<?> type = variableType(node.getChild(0), variables, problems);
            for (int i = 1; i < node.getChildCount() && type != null; i++) {
                type = memberType(type, node.getChild(i), problems);
            }
            return;
        }
        if (node instanceof VariableReference) {
            variableType(node, variables, problems);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            check(node.getChild(i), variables, problems);
        }
    }

    private static Class<?> variableType(final SpelNode node, final Map<String, Type> variables,
                                         final List<String> problems) {
        if (!(node instanceof VariableReference)) {
            return null;
        }
        final String name = node.toStringAST().substring(1);
        if (!variables.containsKey(name)) {
            problems.add("#" + name + " is not a parameter of the method, nor #result");
            return null;
        }
        return rawClass(variables.get(name));
    }

    private static Class<?> memberType(final Class<?> type, final SpelNode node, final List<String> problems) {
        if (node instanceof PropertyOrFieldReference property) {
            final Method accessor = accessor(type, property.getName());
            if (accessor == null) {
                problems.add(type.getSimpleName() + " has no property " + property.getName());
                return null;
            }
            return rawClass(accessor.getGenericReturnType());
        }
        if (node instanceof MethodReference reference) {
            final Method found = Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(reference.getName()) && m.getParameterCount() == node.getChildCount())
                .findFirst().orElse(null);
            if (found == null) {
                problems.add(type.getSimpleName() + " has no method " + reference.getName() + "()");
                return null;
            }
            return rawClass(found.getGenericReturnType());
        }
        return null;
    }

    private static Method accessor(final Class<?> type, final String name) {
        if (type.isRecord()) {
            for (final RecordComponent component : type.getRecordComponents()) {
                if (component.getName().equals(name)) {
                    return component.getAccessor();
                }
            }
        }
        final String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (final String candidate : List.of("get" + suffix, "is" + suffix)) {
            try {
                return type.getMethod(candidate);
            } catch (NoSuchMethodException ignored) {
                // Not this spelling of the accessor; try the next.
            }
        }
        return null;
    }

    private static Class<?> rawClass(final Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        return type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : null;
    }

    record Request(String shortCode) {
    }

    record Created(String name) {
    }

    static class Sample {

        public Created create(final String clientId, final Request request) {
            return new Created(clientId + request.shortCode());
        }
    }
}
