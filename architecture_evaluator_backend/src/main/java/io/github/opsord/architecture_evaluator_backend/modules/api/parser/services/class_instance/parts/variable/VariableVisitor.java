package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.VariableInstance;

import java.util.List;

public class VariableVisitor extends VoidVisitorAdapter<List<VariableInstance>> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<VariableInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, List<VariableInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(RecordDeclaration n, List<VariableInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(AnnotationDeclaration n, List<VariableInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(LocalClassDeclarationStmt n, List<VariableInstance> arg) {
        // Do not descend into local class declarations
    }

    @Override
    public void visit(ObjectCreationExpr n, List<VariableInstance> arg) {
        n.getScope().ifPresent(s -> s.accept(this, arg));
        n.getArguments().forEach(a -> a.accept(this, arg));
    }

    @Override
    public void visit(VariableDeclarator variable, List<VariableInstance> collector) {
        super.visit(variable, collector);
        VariableInstance variableInstance = new VariableInstance();
        variableInstance.setName(variable.getNameAsString());
        variableInstance.setType(variable.getType().asString());

        var parentNodeOpt = variable.getParentNode();
        if (parentNodeOpt.isPresent() && parentNodeOpt.get() instanceof FieldDeclaration fieldDecl) {
            variableInstance.setScope(isStaticField(fieldDecl) ? "static" : "instance");
        } else {
            variableInstance.setScope("local");
        }

        collector.add(variableInstance);
    }

    public static boolean isStaticField(FieldDeclaration fieldDecl) {
        if (fieldDecl.isStatic()) {
            return true;
        }
        return fieldDecl.getParentNode()
                .map(parent -> (parent instanceof ClassOrInterfaceDeclaration c && c.isInterface())
                        || parent instanceof AnnotationDeclaration)
                .orElse(false);
    }
}
