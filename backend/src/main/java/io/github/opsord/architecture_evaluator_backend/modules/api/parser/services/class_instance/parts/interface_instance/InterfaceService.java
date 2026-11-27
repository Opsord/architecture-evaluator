// InterfaceService.java
package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.interface_instance;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InterfaceService {

    public List<String> getImplementedInterfaces(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classDecl) {
            if (classDecl.isInterface()) {
                return List.of();
            }
            return classDecl.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();
        }
        if (declaration instanceof RecordDeclaration recordDecl) {
            return recordDecl.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();
        }
        if (declaration instanceof EnumDeclaration enumDecl) {
            return enumDecl.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();
        }
        return List.of();
    }
}
