package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.constructor;

import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.ConstructorInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.ParameterInstance;

import java.util.List;

public class ConstructorVisitor extends VoidVisitorAdapter<List<ConstructorInstance>> {

    @Override
    public void visit(ConstructorDeclaration constructor, List<ConstructorInstance> collector) {
        ConstructorInstance ci = new ConstructorInstance();
        ci.setName(constructor.getNameAsString());
        ci.setParameters(mapParameters(constructor.getParameters()));
        ci.setAnnotations(
                constructor.getAnnotations().stream().map(NodeWithName::getNameAsString).toList());
        ci.setModifiers(constructor.getModifiers().stream().map(Object::toString).toList());
        ci.setThrownExceptions(
                constructor.getThrownExceptions().stream().map(Object::toString).toList());
        ci.setLineCount(constructor.getBody().getStatements().size());
        collector.add(ci);
    }

    @Override
    public void visit(CompactConstructorDeclaration constructor, List<ConstructorInstance> collector) {
        ConstructorInstance ci = new ConstructorInstance();
        ci.setName(constructor.getNameAsString());
        List<ParameterInstance> params = constructor.getParentNode()
                .filter(RecordDeclaration.class::isInstance)
                .map(RecordDeclaration.class::cast)
                .map(record -> mapParameters(record.getParameters()))
                .orElse(List.of());
        ci.setParameters(params);
        ci.setAnnotations(
                constructor.getAnnotations().stream().map(NodeWithName::getNameAsString).toList());
        ci.setModifiers(constructor.getModifiers().stream().map(Object::toString).toList());
        ci.setThrownExceptions(
                constructor.getThrownExceptions().stream().map(Object::toString).toList());
        ci.setLineCount(constructor.getBody().getStatements().size());
        collector.add(ci);
    }

    private List<ParameterInstance> mapParameters(List<Parameter> parameters) {
        return parameters.stream().map(p -> {
            ParameterInstance param = new ParameterInstance();
            param.setName(p.getNameAsString());
            param.setType(p.getType().asString());
            param.setAnnotations(
                    p.getAnnotations().stream().map(NodeWithName::getNameAsString).toList());
            return param;
        }).toList();
    }
}
