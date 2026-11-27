package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementType;

import java.util.List;

public class StatementVisitor extends VoidVisitorAdapter<List<StatementInstance>> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<StatementInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, List<StatementInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(RecordDeclaration n, List<StatementInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(AnnotationDeclaration n, List<StatementInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(LocalClassDeclarationStmt n, List<StatementInstance> arg) {
        // Do not descend into local class declarations
    }

    @Override
    public void visit(ObjectCreationExpr n, List<StatementInstance> arg) {
        n.getScope().ifPresent(s -> s.accept(this, arg));
        n.getArguments().forEach(a -> a.accept(this, arg));
    }

    @Override
    public void visit(IfStmt ifStmt, List<StatementInstance> collector) {
        super.visit(ifStmt, collector);
        addStatement(StatementType.IF, collector);
    }

    @Override
    public void visit(ForStmt forStmt, List<StatementInstance> collector) {
        super.visit(forStmt, collector);
        addStatement(StatementType.FOR, collector);
    }

    @Override
    public void visit(ForEachStmt forEachStmt, List<StatementInstance> collector) {
        super.visit(forEachStmt, collector);
        addStatement(StatementType.FOR_EACH, collector);
    }

    @Override
    public void visit(WhileStmt whileStmt, List<StatementInstance> collector) {
        super.visit(whileStmt, collector);
        addStatement(StatementType.WHILE, collector);
    }

    @Override
    public void visit(DoStmt doStmt, List<StatementInstance> collector) {
        super.visit(doStmt, collector);
        addStatement(StatementType.DO_WHILE, collector);
    }

    @Override
    public void visit(SwitchStmt switchStmt, List<StatementInstance> collector) {
        super.visit(switchStmt, collector);
        addStatement(StatementType.SWITCH, collector);
    }

    @Override
    public void visit(ExpressionStmt expressionStmt, List<StatementInstance> collector) {
        super.visit(expressionStmt, collector);
        StatementInstance statementInstance = new StatementInstance();
        statementInstance.setType(StatementType.EXPRESSION);
        if (expressionStmt.getExpression() instanceof MethodCallExpr call) {
            statementInstance.setStructure(call.getNameAsString());
        }
        collector.add(statementInstance);
    }

    @Override
    public void visit(ReturnStmt returnStmt, List<StatementInstance> collector) {
        super.visit(returnStmt, collector);
        addStatement(StatementType.RETURN, collector);
    }

    @Override
    public void visit(ThrowStmt throwStmt, List<StatementInstance> collector) {
        super.visit(throwStmt, collector);
        addStatement(StatementType.THROW, collector);
    }

    @Override
    public void visit(TryStmt tryStmt, List<StatementInstance> collector) {
        super.visit(tryStmt, collector);
        addStatement(StatementType.TRY, collector);
    }

    @Override
    public void visit(CatchClause catchClause, List<StatementInstance> collector) {
        super.visit(catchClause, collector);
        addStatement(StatementType.CATCH, collector);
    }

    private void addStatement(StatementType type, List<StatementInstance> collector) {
        StatementInstance statementInstance = new StatementInstance();
        statementInstance.setType(type);
        collector.add(statementInstance);
    }
}
