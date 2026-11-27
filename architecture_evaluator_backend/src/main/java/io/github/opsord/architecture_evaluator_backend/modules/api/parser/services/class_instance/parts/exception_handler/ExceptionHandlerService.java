// ExceptionHandlerService.java
package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.exception_handler;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.ExceptionHandlingInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExceptionHandlerService {

    public List<ExceptionHandlingInstance> getExceptionHandling(TypeDeclaration<?> declaration) {
        List<ExceptionHandlingInstance> exceptionHandling = new ArrayList<>();
        if (declaration == null) {
            return exceptionHandling;
        }
        ExceptionHandlerVisitor visitor = new ExceptionHandlerVisitor();
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (!(member instanceof TypeDeclaration<?>)) {
                member.accept(visitor, exceptionHandling);
            }
        }
        return exceptionHandling;
    }
}
