// PackageVisitor.java
package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.package_part;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

public class PackageVisitor extends VoidVisitorAdapter<List<String>> {

    private final boolean collectStatic;

    public PackageVisitor() {
        this(false);
    }

    public PackageVisitor(boolean collectStatic) {
        this.collectStatic = collectStatic;
    }

    @Override
    public void visit(ImportDeclaration importDeclaration, List<String> collector) {
        super.visit(importDeclaration, collector);
        if (importDeclaration.isStatic() != collectStatic) {
            return;
        }
        String name = importDeclaration.getName().asString();
        collector.add(importDeclaration.isAsterisk() ? name + ".*" : name);
    }
}
