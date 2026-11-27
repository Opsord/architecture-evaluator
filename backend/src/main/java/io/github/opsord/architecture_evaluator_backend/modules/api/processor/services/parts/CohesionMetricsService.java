package io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.VariableInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.MethodInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementType;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.parts.CohesionMetricsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CohesionMetricsService {

    private static final String INSTANCE_SCOPE = "instance";

    public CohesionMetricsDTO calculateCohesionMetrics(ClassInstance classInstance) {
        CohesionMetricsDTO metrics = new CohesionMetricsDTO();
        if (classInstance == null) {
            return metrics;
        }

        // Exclude static methods from cohesion (they cannot access instance fields)
        List<MethodInstance> allMethods = classInstance.getMethods() != null
                ? classInstance.getMethods()
                : Collections.emptyList();
        List<MethodInstance> methods = allMethods.stream()
                .filter(m -> m.getBasicInfo() == null || !m.getBasicInfo().isStatic())
                .toList();

        List<VariableInstance> allVars = classInstance.getClassVariables() != null
                ? classInstance.getClassVariables()
                : Collections.emptyList();
        List<VariableInstance> vars = allVars.stream()
                .filter(variableInstance -> INSTANCE_SCOPE.equals(variableInstance.getScope()))
                .toList();

        // Precompute field-sharing adjacency
        boolean[][] fieldGraph = buildFieldAdjacency(methods, vars);

        metrics.setLackOfCohesion1(calculateLCOM1(fieldGraph));
        metrics.setLackOfCohesion2(calculateLCOM2(fieldGraph));
        metrics.setLackOfCohesion3(calculateLCOM3(fieldGraph));
        metrics.setLackOfCohesion4(calculateLCOM3(mergeCallGraph(fieldGraph, methods)));
        metrics.setLackOfCohesion5(calculateLCOM5(methods, vars));

        return metrics;
    }

    private int calculateLCOM1(boolean[][] graph) {
        int disconnectedPairs = 0;
        int n = graph.length;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!graph[i][j]) {
                    disconnectedPairs++;
                }
            }
        }

        return disconnectedPairs;
    }

    private int calculateLCOM2(boolean[][] graph) {
        int disconnected = 0;
        int connected = 0;
        int n = graph.length;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (graph[i][j]) {
                    connected++;
                } else {
                    disconnected++;
                }
            }
        }

        return Math.max(disconnected - connected, 0);
    }

    private int calculateLCOM3(boolean[][] graph) {
        return countConnectedComponents(graph);
    }

    private boolean[][] mergeCallGraph(boolean[][] baseGraph, List<MethodInstance> methods) {
        int n = methods.size();
        boolean[][] graph = new boolean[n][n];

        for (int i = 0; i < n; i++) {
            graph[i] = Arrays.copyOf(baseGraph[i], n);
        }

        for (int i = 0; i < n; i++) {
            Set<String> calls = extractCalledNames(methods.get(i));
            for (int j = 0; j < n; j++) {
                if (i != j && calls.contains(methods.get(j).getName())) {
                    graph[i][j] = graph[j][i] = true;
                }
            }
        }

        return graph;
    }

    private Set<String> extractCalledNames(MethodInstance methodInstance) {
        if (methodInstance.getInvokedMethods() != null) {
            return methodInstance.getInvokedMethods().stream()
                    .filter(Objects::nonNull)
                    .filter(name -> !name.isBlank())
                    .collect(Collectors.toSet());
        }
        if (methodInstance.getStatementsInfo() == null
                || methodInstance.getStatementsInfo().getStatements() == null) {
            return Collections.emptySet();
        }
        return methodInstance.getStatementsInfo().getStatements().stream()
                .filter(statementInstance -> statementInstance.getType() == StatementType.EXPRESSION)
                .map(statementInstance -> statementInstance.getStructure())
                .filter(Objects::nonNull)
                .map(structure -> structure.endsWith("();")
                        ? structure.substring(0, structure.length() - 3).trim()
                        : structure)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Calculates LCOM5 as defined by Henderson-Sellers (1996):
     * LCOM5 = (m - (1/a) * sum(mu(Aj))) / (m - 1)
     */
    private double calculateLCOM5(List<MethodInstance> methods, List<VariableInstance> vars) {
        int m = methods.size();
        int a = vars.size();

        if (m <= 1 || a == 0) {
            return 0.0;
        }

        Set<String> attributeNames = vars.stream()
                .map(VariableInstance::getName)
                .collect(Collectors.toSet());

        long sumDistinctAccesses = methods.stream()
                .mapToLong(method -> {
                    List<VariableInstance> methodVars = method.getMethodVariables();
                    if (methodVars == null) {
                        return 0L;
                    }
                    return methodVars.stream()
                            .filter(v -> INSTANCE_SCOPE.equals(v.getScope()))
                            .map(VariableInstance::getName)
                            .filter(attributeNames::contains)
                            .distinct()
                            .count();
                })
                .sum();

        double meanAccessPerAttribute = (double) sumDistinctAccesses / a;
        double lcom5 = (m - meanAccessPerAttribute) / (m - 1);
        return Math.max(0.0, lcom5);
    }

    private boolean[][] buildFieldAdjacency(List<MethodInstance> methods, List<VariableInstance> vars) {
        int numberOfMethods = methods.size();
        boolean[][] adj = new boolean[numberOfMethods][numberOfMethods];
        Set<String> varNames = vars.stream()
                .map(VariableInstance::getName)
                .collect(Collectors.toSet());

        List<Set<String>> usedFields = methods.stream()
                .map(m -> {
                    List<VariableInstance> methodVars = m.getMethodVariables();
                    if (methodVars == null) {
                        return Collections.<String>emptySet();
                    }
                    return methodVars.stream()
                            .filter(v -> INSTANCE_SCOPE.equals(v.getScope()))
                            .map(VariableInstance::getName)
                            .filter(varNames::contains)
                            .collect(Collectors.toSet());
                })
                .toList();

        for (int i = 0; i < numberOfMethods; i++) {
            for (int j = i + 1; j < numberOfMethods; j++) {
                Set<String> intersection = new HashSet<>(usedFields.get(i));
                intersection.retainAll(usedFields.get(j));
                if (!intersection.isEmpty()) {
                    adj[i][j] = adj[j][i] = true;
                }
            }
        }
        return adj;
    }

    private int countConnectedComponents(boolean[][] adj) {
        int n = adj.length;
        boolean[] visited = new boolean[n];
        int count = 0;
        Deque<Integer> stack = new ArrayDeque<>();

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                count++;
                stack.push(i);
                while (!stack.isEmpty()) {
                    int u = stack.pop();
                    if (!visited[u]) {
                        visited[u] = true;
                        for (int v = 0; v < n; v++) {
                            if (adj[u][v] && !visited[v]) {
                                stack.push(v);
                            }
                        }
                    }
                }
            }
        }
        return count;
    }
}
