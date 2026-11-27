package io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.MethodInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.StatementsInfo;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.parts.ComplexityMetricsDTO;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.parts.ProgramMetricsDTO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class ComplexityMetricsService {

    public ComplexityMetricsDTO calculateComplexityMetrics(ClassInstance classInstance) {
        return calculateComplexityMetrics(classInstance, null);
    }

    public ComplexityMetricsDTO calculateComplexityMetrics(
            ClassInstance classInstance,
            ProgramMetricsDTO programMetrics) {
        ComplexityMetricsDTO complexityMetrics = new ComplexityMetricsDTO();
        if (classInstance == null) {
            return complexityMetrics;
        }

        List<MethodInstance> methods = classInstance.getMethods() != null
                ? classInstance.getMethods()
                : Collections.emptyList();

        int compUnitApproxMcCabeCC = 0;
        int numberOfMethods = programMetrics != null ? programMetrics.getNumberOfMethods() : methods.size();
        int sumOfExecutableStatements = programMetrics != null
                ? programMetrics.getSumOfExecutableStatements()
                : countExecutableStatements(classInstance.getStatements());
        int maxInputParameters = programMetrics != null ? programMetrics.getMaxInputParameters() : 0;
        int maxOutputParameters = programMetrics != null ? programMetrics.getMaxOutputParameters() : 0;
        int totalLinesOfCode = programMetrics != null ? programMetrics.getLinesOfCode() : classInstance.getLinesOfCode();

        for (MethodInstance method : methods) {
            if (programMetrics == null) {
                int inputParameters = method.getInputParameters() != null ? method.getInputParameters().size() : 0;
                int outputParameters = method.getOutputParameters() != null ? method.getOutputParameters().size() : 0;
                maxInputParameters = Math.max(maxInputParameters, inputParameters);
                maxOutputParameters = Math.max(maxOutputParameters, outputParameters);
            }

            int methodMcCabeCC = calculateApproxMcCabeCC(method);
            if (method.getMethodMetrics() != null) {
                method.getMethodMetrics().setMcCabeComplexity(methodMcCabeCC);
            }
            complexityMetrics.setMaxMethodMcCabeCC(Math.max(complexityMetrics.getMaxMethodMcCabeCC(), methodMcCabeCC));
            compUnitApproxMcCabeCC += methodMcCabeCC;
        }

        double improvedCC = calculateImprovedCC(numberOfMethods, sumOfExecutableStatements, maxInputParameters,
                maxOutputParameters, totalLinesOfCode);

        complexityMetrics.setApproxMcCabeCC(compUnitApproxMcCabeCC);
        complexityMetrics.setImprovedCC(improvedCC);

        return complexityMetrics;
    }

    private int countExecutableStatements(List<StatementInstance> statements) {
        if (statements == null) {
            return 0;
        }
        int count = 0;
        for (StatementInstance statement : statements) {
            if (statement != null && StatementService.isExecutableStatement(statement.getType())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Standard McCabe Cyclomatic Complexity:
     * CC = 1 + controlStatements + switchCases + catchClauses + logicalOperators + ternaryOperators
     * (WMC convention: methods without a body or with null StatementsInfo have CC = 1.)
     */
    private int calculateApproxMcCabeCC(MethodInstance methodInstance) {
        StatementsInfo info = methodInstance.getStatementsInfo();
        if (info == null) {
            return 1;
        }
        return 1
                + info.getNumberOfControlStatements()
                + info.getNumberOfSwitchCases()
                + info.getNumberOfCatchClauses()
                + info.getNumberOfLogicalOperators()
                + info.getNumberOfTernaryOperators();
    }

    private double calculateImprovedCC(int numberOfMethods, int sumOfExecutableStatements, int maxInputParameters,
                                       int maxOutputParameters, int totalLinesOfCode) {
        return totalLinesOfCode > 0
                ? (double) (numberOfMethods + sumOfExecutableStatements + maxInputParameters + maxOutputParameters)
                / totalLinesOfCode
                : 0;
    }
}
