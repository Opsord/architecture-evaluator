package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.constructor;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.ConstructorInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConstructorService {

    public List<ConstructorInstance> getConstructors(TypeDeclaration<?> declaration) {
        List<ConstructorInstance> constructors = new ArrayList<>();
        if (declaration == null) {
            return constructors;
        }
        ConstructorVisitor visitor = new ConstructorVisitor();
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof ConstructorDeclaration || member instanceof CompactConstructorDeclaration) {
                member.accept(visitor, constructors);
            }
        }
        return constructors;
    }
}
