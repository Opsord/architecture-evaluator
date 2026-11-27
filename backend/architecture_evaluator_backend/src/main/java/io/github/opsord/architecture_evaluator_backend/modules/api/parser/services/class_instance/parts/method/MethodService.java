package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.method;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.MethodInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MethodService {

    private final StatementService statementService;

    public List<MethodInstance> getMethods(TypeDeclaration<?> declaration) {
        return getMethods(declaration, Map.of(), declaration != null ? declaration.getNameAsString() : null);
    }

    public List<MethodInstance> getMethods(TypeDeclaration<?> declaration, Map<String, String> instanceFieldsByName) {
        return getMethods(declaration, instanceFieldsByName, declaration != null ? declaration.getNameAsString() : null);
    }

    public List<MethodInstance> getMethods(
            TypeDeclaration<?> declaration,
            Map<String, String> instanceFieldsByName,
            String enclosingTypeName) {
        List<MethodInstance> methods = new ArrayList<>();
        if (declaration == null) {
            return methods;
        }
        MethodVisitor visitor = new MethodVisitor(statementService, instanceFieldsByName, enclosingTypeName);
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof MethodDeclaration) {
                member.accept(visitor, methods);
            }
        }
        return methods;
    }
}
