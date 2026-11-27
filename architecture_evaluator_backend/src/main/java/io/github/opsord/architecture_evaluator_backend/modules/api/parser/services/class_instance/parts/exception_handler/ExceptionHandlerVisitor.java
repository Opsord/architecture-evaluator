// ExceptionHandlerVisitor.java
package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.exception_handler;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.ExceptionHandlingInstance;

import java.util.List;

public class ExceptionHandlerVisitor extends VoidVisitorAdapter<List<ExceptionHandlingInstance>> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<ExceptionHandlingInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, List<ExceptionHandlingInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(RecordDeclaration n, List<ExceptionHandlingInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(AnnotationDeclaration n, List<ExceptionHandlingInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(LocalClassDeclarationStmt n, List<ExceptionHandlingInstance> arg) {
        // Do not descend into local class declarations
    }

    @Override
    public void visit(ObjectCreationExpr n, List<ExceptionHandlingInstance> arg) {
        n.getScope().ifPresent(s -> s.accept(this, arg));
        n.getArguments().forEach(a -> a.accept(this, arg));
    }

    @Override
    public void visit(TryStmt tryStmt, List<ExceptionHandlingInstance> collector) {
        super.visit(tryStmt, collector);
        ExceptionHandlingInstance exceptionHandlingInstance = new ExceptionHandlingInstance();
        exceptionHandlingInstance.setCatchBlocks(tryStmt.getCatchClauses().stream()
                .map(clause -> clause.getParameter().getType().asString())
                .toList());
        collector.add(exceptionHandlingInstance);
    }
}
