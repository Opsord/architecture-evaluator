package io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementInstance;
import lombok.Data;

import java.util.List;

@Data
public class StatementsInfo {
    private int numberOfStatements;              // all statements
    private int numberOfExecutableStatements;    // IF, FOR, FOR_EACH, WHILE, DO_WHILE, SWITCH, EXPRESSION, RETURN, THROW, TRY
    private int numberOfControlStatements;       // IF, FOR, FOR_EACH, WHILE, DO_WHILE
    private int numberOfSwitchCases;             // case labels + when guards across SwitchStmt / SwitchExpr
    private int numberOfReturnStatements;        // RETURN
    private int numberOfThrowStatements;         // THROW
    private int numberOfCatchClauses;            // CATCH
    private int numberOfLogicalOperators;        // "&&" or "||"
    private int numberOfTernaryOperators;        // ?:

    private List<StatementInstance> statements;  // raw AST fragments
}
