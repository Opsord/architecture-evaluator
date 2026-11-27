// StatementService.java
package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.StatementsInfo;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatementService {

    public List<StatementInstance> getStatements(TypeDeclaration<?> declaration) {
        List<StatementInstance> statements = new ArrayList<>();
        if (declaration == null) {
            return statements;
        }
        StatementVisitor visitor = new StatementVisitor();
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof MethodDeclaration
                    || member instanceof ConstructorDeclaration
                    || member instanceof CompactConstructorDeclaration
                    || member instanceof InitializerDeclaration) {
                member.accept(visitor, statements);
            }
        }
        return statements;
    }

    public List<StatementInstance> getStatementsFromMethod(MethodDeclaration method) {
        if (method == null) {
            return List.of();
        }
        return extractStatements(method);
    }

    private List<StatementInstance> extractStatements(Node node) {
        List<StatementInstance> statements = new ArrayList<>();
        StatementVisitor visitor = new StatementVisitor();
        node.accept(visitor, statements);
        return statements;
    }

    public static boolean isExecutableStatement(StatementType type) {
        return type == StatementType.IF
                || type == StatementType.FOR
                || type == StatementType.FOR_EACH
                || type == StatementType.WHILE
                || type == StatementType.DO_WHILE
                || type == StatementType.SWITCH
                || type == StatementType.EXPRESSION
                || type == StatementType.RETURN
                || type == StatementType.THROW
                || type == StatementType.TRY;
    }

    public int countExecutableStatements(List<StatementInstance> statements) {
        if (statements == null) {
            return 0;
        }
        int count = 0;
        for (StatementInstance statement : statements) {
            if (statement != null && isExecutableStatement(statement.getType())) {
                count++;
            }
        }
        return count;
    }

    public StatementsInfo getStatementsInfoFromMethod(MethodDeclaration method) {
        List<StatementInstance> statements = getStatementsFromMethod(method);
        StatementsInfo info = new StatementsInfo();
        info.setStatements(statements);
        info.setNumberOfStatements(statements.size());

        int controlCount = 0;
        int executableCount = 0;
        int returnCount = 0;
        int throwCount = 0;
        int catchCount = 0;

        for (StatementInstance s : statements) {
            if (s == null || s.getType() == null) {
                continue;
            }
            StatementType type = s.getType();
            if (isExecutableStatement(type)) {
                executableCount++;
            }
            switch (type) {
                case IF, FOR, FOR_EACH, WHILE, DO_WHILE -> controlCount++;
                case RETURN -> returnCount++;
                case THROW -> throwCount++;
                case CATCH -> catchCount++;
                default -> {
                    // EXPRESSION, SWITCH, TRY handled elsewhere
                }
            }
        }

        info.setNumberOfControlStatements(controlCount);
        info.setNumberOfExecutableStatements(executableCount);
        info.setNumberOfReturnStatements(returnCount);
        info.setNumberOfThrowStatements(throwCount);
        info.setNumberOfCatchClauses(catchCount);

        if (method != null) {
            method.getBody().ifPresent(body -> {
                BranchExpressionCounter counter = new BranchExpressionCounter();
                body.accept(counter, null);
                info.setNumberOfLogicalOperators(counter.logicalOperators);
                info.setNumberOfTernaryOperators(counter.ternaryOperators);
                info.setNumberOfSwitchCases(counter.switchCases);
            });
        }
        return info;
    }

    private static final class BranchExpressionCounter extends VoidVisitorAdapter<Void> {
        int logicalOperators = 0;
        int ternaryOperators = 0;
        int switchCases = 0;

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(EnumDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(RecordDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(AnnotationDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(LocalClassDeclarationStmt n, Void arg) {
            // Do not descend into local class declarations
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            n.getScope().ifPresent(s -> s.accept(this, arg));
            n.getArguments().forEach(a -> a.accept(this, arg));
        }

        @Override
        public void visit(BinaryExpr n, Void arg) {
            if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
                logicalOperators++;
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(ConditionalExpr n, Void arg) {
            ternaryOperators++;
            super.visit(n, arg);
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            for (SwitchEntry entry : n.getEntries()) {
                countSwitchEntry(entry);
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(SwitchExpr n, Void arg) {
            for (SwitchEntry entry : n.getEntries()) {
                countSwitchEntry(entry);
            }
            super.visit(n, arg);
        }

        private void countSwitchEntry(SwitchEntry entry) {
            switchCases += entry.getLabels().size();
            if (entry.getGuard().isPresent()) {
                switchCases++;
            }
        }
    }
}
