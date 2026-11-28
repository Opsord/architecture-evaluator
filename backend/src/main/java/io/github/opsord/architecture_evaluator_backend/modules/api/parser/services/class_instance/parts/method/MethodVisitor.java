package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.method;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.RecordPatternExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.ParameterInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.VariableInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.MethodInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.BasicInfo;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.MethodMetrics;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.StatementsInfo;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable.VariableVisitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

public class MethodVisitor extends VoidVisitorAdapter<List<MethodInstance>> {

    private final StatementService statementService;
    private final Map<String, String> instanceFieldsByName;
    private final String enclosingTypeName;

    public MethodVisitor(StatementService statementService) {
        this(statementService, Map.of(), null);
    }

    public MethodVisitor(
            StatementService statementService,
            Map<String, String> instanceFieldsByName,
            String enclosingTypeName) {
        this.statementService = statementService;
        this.instanceFieldsByName = instanceFieldsByName != null ? instanceFieldsByName : Map.of();
        this.enclosingTypeName = enclosingTypeName;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<MethodInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, List<MethodInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(RecordDeclaration n, List<MethodInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(AnnotationDeclaration n, List<MethodInstance> arg) {
        if (n.isNestedType()) {
            return;
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(LocalClassDeclarationStmt n, List<MethodInstance> arg) {
        // Do not descend into local class declarations
    }

    @Override
    public void visit(ObjectCreationExpr n, List<MethodInstance> arg) {
        n.getScope().ifPresent(s -> s.accept(this, arg));
        n.getArguments().forEach(a -> a.accept(this, arg));
    }

    @Override
    public void visit(MethodDeclaration method, List<MethodInstance> collector) {
        MethodInstance methodInstance = new MethodInstance();
        methodInstance.setName(method.getNameAsString());
        methodInstance.setBasicInfo(populateBasicInfo(method));
        methodInstance.setStatementsInfo(populateStatementInfo(method));
        methodInstance.setInputParameters(populateInputParameters(method));
        methodInstance.setOutputParameters(populateOutputParameters(method));
        methodInstance.setMethodMetrics(populateMethodMetrics(method));

        List<VariableInstance> variables = extractLocalVariables(method);
        Set<String> accessedFields = new LinkedHashSet<>();
        Set<String> invokedMethods = new LinkedHashSet<>();

        if (!method.isStatic()) {
            LexicalBodyAnalyzer analyzer = new LexicalBodyAnalyzer(
                    instanceFieldsByName, enclosingTypeName, accessedFields, invokedMethods);
            analyzer.analyze(method);
            for (String fieldName : accessedFields) {
                VariableInstance accessed = new VariableInstance();
                accessed.setName(fieldName);
                accessed.setType(instanceFieldsByName.get(fieldName));
                accessed.setScope("instance");
                variables.add(accessed);
            }
        }

        methodInstance.setMethodVariables(variables);
        methodInstance.setInvokedMethods(new ArrayList<>(invokedMethods));
        collector.add(methodInstance);
    }

    private BasicInfo populateBasicInfo(MethodDeclaration method) {
        BasicInfo basicInfo = new BasicInfo();
        basicInfo.setAccessModifier(method.getAccessSpecifier().asString());
        basicInfo.setReturnType(method.getType().asString());
        basicInfo.setStatic(method.isStatic());
        basicInfo.setHasBody(method.getBody().isPresent());
        return basicInfo;
    }

    private StatementsInfo populateStatementInfo(MethodDeclaration method) {
        return statementService.getStatementsInfoFromMethod(method);
    }

    private List<ParameterInstance> populateInputParameters(MethodDeclaration method) {
        return method.getParameters().stream().map(p -> {
            ParameterInstance param = new ParameterInstance();
            param.setName(p.getNameAsString());
            param.setType(p.getType().asString());
            param.setAnnotations(
                    p.getAnnotations().stream().map(NodeWithName::getNameAsString).toList());
            return param;
        }).toList();
    }

    private List<ParameterInstance> populateOutputParameters(MethodDeclaration method) {
        List<ParameterInstance> outputs = new ArrayList<>();
        if (!method.getType().isVoidType()) {
            ParameterInstance output = new ParameterInstance();
            output.setName("return");
            output.setType(method.getType().asString());
            output.setAnnotations(new ArrayList<>());
            outputs.add(output);
        }
        return outputs;
    }

    private MethodMetrics populateMethodMetrics(MethodDeclaration method) {
        MethodMetrics methodMetrics = new MethodMetrics();
        int linesOfCode = method.getBody()
                .map(body -> body.getTokenRange()
                        .map(tokens -> Math.toIntExact(StreamSupport.stream(tokens.spliterator(), false)
                                .filter(token -> !token.toString().startsWith("//")
                                        && !token.toString().startsWith("/*"))
                                .map(token -> token.getRange().map(range -> range.begin.line).orElse(0))
                                .distinct()
                                .count()))
                        .orElse(0))
                .orElse(0);
        methodMetrics.setLinesOfCode(linesOfCode);
        return methodMetrics;
    }

    private List<VariableInstance> extractLocalVariables(MethodDeclaration method) {
        List<VariableInstance> variables = new ArrayList<>();
        VariableVisitor visitor = new VariableVisitor();
        method.getBody().ifPresent(body -> body.accept(visitor, variables));
        return variables;
    }

    private static final class LexicalBodyAnalyzer extends VoidVisitorAdapter<Void> {

        private final Map<String, String> instanceFieldsByName;
        private final String enclosingTypeName;
        private final Set<String> accessedFields;
        private final Set<String> invokedMethods;
        private final Deque<Set<String>> scopes = new ArrayDeque<>();

        private LexicalBodyAnalyzer(
                Map<String, String> instanceFieldsByName,
                String enclosingTypeName,
                Set<String> accessedFields,
                Set<String> invokedMethods) {
            this.instanceFieldsByName = instanceFieldsByName;
            this.enclosingTypeName = enclosingTypeName;
            this.accessedFields = accessedFields;
            this.invokedMethods = invokedMethods;
        }

        private void analyze(MethodDeclaration method) {
            if (method.getBody().isEmpty()) {
                return;
            }
            Set<String> methodScope = new HashSet<>();
            method.getParameters().forEach(p -> methodScope.add(p.getNameAsString()));
            scopes.push(methodScope);
            try {
                method.getBody().get().accept(this, null);
            } finally {
                scopes.pop();
            }
        }

        private boolean isShadowed(String name) {
            for (Set<String> scope : scopes) {
                if (scope.contains(name)) {
                    return true;
                }
            }
            return false;
        }

        private void declareInCurrentScope(String name) {
            if (!scopes.isEmpty()) {
                scopes.peek().add(name);
            }
        }

        private boolean isCurrentThis(Expression expr) {
            if (!(expr instanceof ThisExpr thisExpr)) {
                return false;
            }
            return thisExpr.getTypeName().isEmpty()
                    || (enclosingTypeName != null
                            && enclosingTypeName.equals(thisExpr.getTypeName().get().getIdentifier()));
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(EnumDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(RecordDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(AnnotationDeclaration n, Void arg) {
            // Do not descend into nested types
        }

        @Override
        public void visit(LocalClassDeclarationStmt n, Void arg) {
            // Do not descend into local class declarations
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            n.getScope().ifPresent(s -> s.accept(this, arg));
            n.getArguments().forEach(a -> a.accept(this, arg));
        }

        @Override
        public void visit(BlockStmt n, Void arg) {
            scopes.push(new HashSet<>());
            try {
                super.visit(n, arg);
            } finally {
                scopes.pop();
            }
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            scopes.push(new HashSet<>());
            try {
                n.getInitialization().forEach(e -> e.accept(this, arg));
                n.getCompare().ifPresent(e -> e.accept(this, arg));
                n.getUpdate().forEach(e -> e.accept(this, arg));
                n.getBody().accept(this, arg);
            } finally {
                scopes.pop();
            }
        }

        @Override
        public void visit(ForEachStmt n, Void arg) {
            // Visit iterable BEFORE pushing loop scope so "for (String items : items)" sees instance field "items"
            n.getIterable().accept(this, arg);
            scopes.push(new HashSet<>());
            try {
                n.getVariable().accept(this, arg);
                n.getBody().accept(this, arg);
            } finally {
                scopes.pop();
            }
        }

        @Override
        public void visit(TryStmt n, Void arg) {
            scopes.push(new HashSet<>());
            try {
                n.getResources().forEach(r -> r.accept(this, arg));
                n.getTryBlock().accept(this, arg);
            } finally {
                scopes.pop();
            }
            n.getCatchClauses().forEach(c -> c.accept(this, arg));
            n.getFinallyBlock().ifPresent(f -> f.accept(this, arg));
        }

        @Override
        public void visit(CatchClause n, Void arg) {
            scopes.push(new HashSet<>());
            try {
                declareInCurrentScope(n.getParameter().getNameAsString());
                n.getBody().accept(this, arg);
            } finally {
                scopes.pop();
            }
        }

        @Override
        public void visit(LambdaExpr n, Void arg) {
            scopes.push(new HashSet<>());
            try {
                n.getParameters().forEach(p -> declareInCurrentScope(p.getNameAsString()));
                n.getBody().accept(this, arg);
            } finally {
                scopes.pop();
            }
        }

        @Override
        public void visit(IfStmt n, Void arg) {
            scopes.push(new HashSet<>());
            try {
                n.getCondition().accept(this, arg);
                n.getThenStmt().accept(this, arg);
            } finally {
                scopes.pop();
            }
            n.getElseStmt().ifPresent(e -> e.accept(this, arg));
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            n.getSelector().accept(this, arg);
            scopes.push(new HashSet<>());
            try {
                n.getEntries().forEach(entry -> visitSwitchEntry(entry, arg));
            } finally {
                scopes.pop();
            }
        }

        @Override
        public void visit(SwitchExpr n, Void arg) {
            n.getSelector().accept(this, arg);
            scopes.push(new HashSet<>());
            try {
                n.getEntries().forEach(entry -> visitSwitchEntry(entry, arg));
            } finally {
                scopes.pop();
            }
        }

        private void visitSwitchEntry(SwitchEntry entry, Void arg) {
            if (entry.getType() == SwitchEntry.Type.STATEMENT_GROUP) {
                // Pattern labels & guard scoped to entry; local variable declarations live in enclosing switch scope
                Set<String> entryPatterns = new HashSet<>();
                scopes.push(entryPatterns);
                try {
                    entry.getLabels().forEach(l -> l.accept(this, arg));
                    entry.getGuard().ifPresent(g -> g.accept(this, arg));
                } finally {
                    scopes.pop();
                }

                Set<String> switchScope = scopes.peek();
                if (switchScope != null && !entryPatterns.isEmpty()) {
                    Set<String> entryScope = new HashSet<>(entryPatterns);
                    scopes.push(entryScope);
                    try {
                        entry.getStatements().forEach(s -> s.accept(this, arg));
                    } finally {
                        scopes.pop();
                        entryScope.removeAll(entryPatterns);
                        switchScope.addAll(entryScope);
                    }
                } else {
                    entry.getStatements().forEach(s -> s.accept(this, arg));
                }
                return;
            }
            scopes.push(new HashSet<>());
            try {
                entry.getLabels().forEach(l -> l.accept(this, arg));
                entry.getGuard().ifPresent(g -> g.accept(this, arg));
                entry.getStatements().forEach(s -> s.accept(this, arg));
            } finally {
                scopes.pop();
            }
        }

        @Override
        public void visit(VariableDeclarator n, Void arg) {
            // Visit initializer BEFORE declaring the variable name so "int x = x + 1;" sees the field
            n.getInitializer().ifPresent(init -> init.accept(this, arg));
            declareInCurrentScope(n.getNameAsString());
        }

        @Override
        public void visit(TypePatternExpr n, Void arg) {
            declareInCurrentScope(n.getNameAsString());
        }

        @Override
        public void visit(RecordPatternExpr n, Void arg) {
            n.getPatternList().forEach(p -> p.accept(this, arg));
        }

        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            Expression scope = n.getScope();
            if (scope instanceof SuperExpr) {
                return;
            }
            if (isCurrentThis(scope)) {
                String fieldName = n.getNameAsString();
                if (instanceFieldsByName.containsKey(fieldName)) {
                    accessedFields.add(fieldName);
                }
                return;
            }
            scope.accept(this, arg);
        }

        @Override
        public void visit(NameExpr n, Void arg) {
            String name = n.getNameAsString();
            if (instanceFieldsByName.containsKey(name) && !isShadowed(name)) {
                accessedFields.add(name);
            }
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            if (n.getScope().isEmpty() || isCurrentThis(n.getScope().get())) {
                invokedMethods.add(n.getNameAsString());
            }
            n.getScope().ifPresent(s -> s.accept(this, arg));
            n.getArguments().forEach(a -> a.accept(this, arg));
        }

        @Override
        public void visit(MethodReferenceExpr n, Void arg) {
            if (isCurrentThis(n.getScope())) {
                invokedMethods.add(n.getIdentifier());
            }
            n.getScope().accept(this, arg);
        }
    }
}
