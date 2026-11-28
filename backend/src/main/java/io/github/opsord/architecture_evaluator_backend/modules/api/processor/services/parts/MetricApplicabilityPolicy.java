package io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.JavaFileType;

/**
 * Centralizes metric applicability rules per {@link JavaFileType}.
 */
public final class MetricApplicabilityPolicy {

    private MetricApplicabilityPolicy() {
    }

    /**
     * Cohesion metrics (LCOM1-5) measure collaboration over mutable instance state
     * and only apply to concrete/abstract classes (plus {@code null}/{@code UNKNOWN}
     * for backwards compatibility with synthetic test instances).
     */
    public static boolean supportsCohesion(JavaFileType type) {
        if (type == null) {
            return true;
        }
        return switch (type) {
            case INTERFACE, ANNOTATION, ENUM, EXCEPTION, RECORD -> false;
            case CLASS, ABSTRACT_CLASS, UNKNOWN -> true;
        };
    }

    /**
     * Contract declarations ({@code INTERFACE}, {@code ANNOTATION}) do not contribute
     * cyclomatic complexity for bodyless method signatures.
     */
    public static boolean countBodylessMethodsInComplexity(JavaFileType type) {
        if (type == null) {
            return true;
        }
        return switch (type) {
            case INTERFACE, ANNOTATION -> false;
            case CLASS, ABSTRACT_CLASS, ENUM, RECORD, EXCEPTION, UNKNOWN -> true;
        };
    }
}
