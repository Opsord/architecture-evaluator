package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.VariableInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VariableService {

    public List<VariableInstance> getVariables(TypeDeclaration<?> declaration) {
        List<VariableInstance> variables = new ArrayList<>();
        if (declaration == null) {
            return variables;
        }
        if (declaration instanceof RecordDeclaration recordDecl) {
            for (Parameter component : recordDecl.getParameters()) {
                VariableInstance v = new VariableInstance();
                v.setName(component.getNameAsString());
                v.setType(component.getType().asString());
                v.setScope("instance");
                variables.add(v);
            }
        }
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof FieldDeclaration fieldDecl) {
                String scope = VariableVisitor.isStaticField(fieldDecl) ? "static" : "instance";
                for (VariableDeclarator declarator : fieldDecl.getVariables()) {
                    VariableInstance v = new VariableInstance();
                    v.setName(declarator.getNameAsString());
                    v.setType(declarator.getType().asString());
                    v.setScope(scope);
                    variables.add(v);
                }
            }
        }
        return variables;
    }
}
