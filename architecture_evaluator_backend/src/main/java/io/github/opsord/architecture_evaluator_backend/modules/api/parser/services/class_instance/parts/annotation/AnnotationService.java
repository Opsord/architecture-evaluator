// AnnotationService.java
package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.annotation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnnotationService {

    public List<String> getAnnotationsFromClass(TypeDeclaration<?> declaration) {
        if (declaration == null) {
            return List.of();
        }
        return declaration.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .toList();
    }

    public List<String> getAnnotationsFromFile(CompilationUnit compilationUnit) {
        if (compilationUnit == null) {
            return List.of();
        }
        List<String> annotations = new ArrayList<>();
        compilationUnit.getPackageDeclaration().ifPresent(pkg ->
                pkg.getAnnotations().stream()
                        .map(AnnotationExpr::getNameAsString)
                        .forEach(annotations::add));
        for (TypeDeclaration<?> type : compilationUnit.getTypes()) {
            type.getAnnotations().stream()
                    .map(AnnotationExpr::getNameAsString)
                    .forEach(annotations::add);
        }
        return annotations;
    }
}
