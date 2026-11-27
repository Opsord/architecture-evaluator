package io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement;

import lombok.Getter;

@Getter
public enum StatementType {
    IF,
    FOR,
    FOR_EACH,
    WHILE,
    DO_WHILE,
    SWITCH,
    EXPRESSION,
    RETURN,
    THROW,
    TRY,
    CATCH
}
