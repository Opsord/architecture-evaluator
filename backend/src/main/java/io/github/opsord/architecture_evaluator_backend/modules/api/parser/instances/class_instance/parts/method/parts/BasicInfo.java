package io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BasicInfo {
    private String accessModifier;
    private String returnType;
    @JsonProperty("isStatic")
    private boolean isStatic;
}
