package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.JavaFileType;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.LayerAnnotation;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.VariableInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.annotation.AnnotationService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.constructor.ConstructorService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.exception_handler.ExceptionHandlerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.interface_instance.InterfaceService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.method.MethodService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable.VariableService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class ClassVisitor extends VoidVisitorAdapter<List<ClassInstance>> {

    private final MethodService methodService;
    private final StatementService statementService;
    private final VariableService variableService;
    private final ConstructorService constructorService;
    private final AnnotationService annotationService;
    private final InterfaceService interfaceService;
    private final ExceptionHandlerService exceptionHandlerService;

    private static final Set<String> ENTITY_ANNOTATIONS = Set.of("Entity");
    private static final Set<String> DOCUMENT_ANNOTATIONS = Set.of("Document");
    private static final Set<String> SERVICE_ANNOTATIONS = Set.of("Service");
    private static final Set<String> REPOSITORY_ANNOTATIONS = Set.of("Repository");
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of("Controller", "RestController");
    private static final Set<String> SPRING_DATA_REPOSITORY_INTERFACES = Set.of(
            "JpaRepository",
            "CrudRepository",
            "ListCrudRepository",
            "PagingAndSortingRepository",
            "ListPagingAndSortingRepository",
            "MongoRepository",
            "ReactiveCrudRepository",
            "ReactiveSortingRepository",
            "R2dbcRepository");
    private static final Set<String> TESTING_CLASS_ANNOTATIONS = Set.of(
            "SpringBootTest", "WebMvcTest", "DataJpaTest", "ExtendWith", "Test");
    private static final Set<String> TESTING_METHOD_ANNOTATIONS = Set.of(
            "Test", "ParameterizedTest", "RepeatedTest");

    public static String buildQualifiedName(String packageName, String name) {
        if (name == null) {
            return null;
        }
        if (packageName == null || packageName.isBlank() || "default".equals(packageName)) {
            return name;
        }
        return packageName + "." + name;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration declaration, List<ClassInstance> collector) {
        if (declaration.isNestedType()) {
            return;
        }
        collector.add(parseType(declaration, resolvePackageName(declaration), null));
    }

    @Override
    public void visit(RecordDeclaration declaration, List<ClassInstance> collector) {
        if (declaration.isNestedType()) {
            return;
        }
        collector.add(parseType(declaration, resolvePackageName(declaration), null));
    }

    @Override
    public void visit(EnumDeclaration declaration, List<ClassInstance> collector) {
        if (declaration.isNestedType()) {
            return;
        }
        collector.add(parseType(declaration, resolvePackageName(declaration), null));
    }

    @Override
    public void visit(AnnotationDeclaration declaration, List<ClassInstance> collector) {
        if (declaration.isNestedType()) {
            return;
        }
        collector.add(parseType(declaration, resolvePackageName(declaration), null));
    }

    public ClassInstance parseType(TypeDeclaration<?> declaration, String packageName, String parentFqn) {
        ClassInstance instance = new ClassInstance();
        String simpleName = declaration.getNameAsString();
        instance.setName(simpleName);
        instance.setPackageName(packageName);
        String qualifiedName = parentFqn != null
                ? parentFqn + "." + simpleName
                : buildQualifiedName(packageName, simpleName);
        instance.setQualifiedName(qualifiedName);

        setJavaFileType(declaration, instance);
        setInheritance(declaration, instance);
        setAnnotationsAndLayer(declaration, instance);
        setExceptionHandling(declaration, instance);
        setMembers(declaration, instance);
        setInnerClasses(declaration, instance, packageName);
        instance.setUsedClasses(collectUsedClasses(declaration));
        setLinesOfCode(declaration, instance);

        return instance;
    }

    private String resolvePackageName(TypeDeclaration<?> declaration) {
        return declaration.findCompilationUnit()
                .flatMap(cu -> cu.getPackageDeclaration().map(pd -> pd.getNameAsString()))
                .orElse("default");
    }

    private void setJavaFileType(TypeDeclaration<?> declaration, ClassInstance instance) {
        if (declaration instanceof ClassOrInterfaceDeclaration classDecl) {
            if (classDecl.isInterface()) {
                instance.setJavaFileType(JavaFileType.INTERFACE);
            } else if (extendsExceptionHierarchy(classDecl)) {
                instance.setJavaFileType(JavaFileType.EXCEPTION);
            } else if (classDecl.isAbstract()) {
                instance.setJavaFileType(JavaFileType.ABSTRACT_CLASS);
            } else {
                instance.setJavaFileType(JavaFileType.CLASS);
            }
        } else if (declaration instanceof RecordDeclaration) {
            instance.setJavaFileType(JavaFileType.RECORD);
        } else if (declaration instanceof EnumDeclaration) {
            instance.setJavaFileType(JavaFileType.ENUM);
        } else if (declaration instanceof AnnotationDeclaration) {
            instance.setJavaFileType(JavaFileType.ANNOTATION);
        } else {
            instance.setJavaFileType(JavaFileType.UNKNOWN);
        }
    }

    /**
     * Syntactic heuristic for exception classes (without cross-file symbol resolution).
     * Matches direct supertypes ending in "Exception" or exact "Error" / "Throwable"
     * to avoid false positives on domain DTO hierarchies such as ApiError / BaseError.
     */
    private boolean extendsExceptionHierarchy(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getExtendedTypes().stream()
                .map(NodeWithSimpleName::getNameAsString)
                .anyMatch(name -> name.endsWith("Exception")
                        || "Error".equals(name)
                        || "Throwable".equals(name));
    }

    private void setAnnotationsAndLayer(TypeDeclaration<?> declaration, ClassInstance instance) {
        List<String> annotations = annotationService.getAnnotationsFromClass(declaration);
        instance.setAnnotations(annotations);
        if (annotations == null) {
            instance.setLayerAnnotation(LayerAnnotation.UNKNOWN);
            return;
        }
        if (annotations.stream().anyMatch(ENTITY_ANNOTATIONS::contains)) {
            instance.setLayerAnnotation(LayerAnnotation.ENTITY);
        } else if (annotations.stream().anyMatch(DOCUMENT_ANNOTATIONS::contains)) {
            instance.setLayerAnnotation(LayerAnnotation.DOCUMENT);
        } else if (annotations.stream().anyMatch(SERVICE_ANNOTATIONS::contains)) {
            instance.setLayerAnnotation(LayerAnnotation.SERVICE);
        } else if (annotations.stream().anyMatch(REPOSITORY_ANNOTATIONS::contains)) {
            instance.setLayerAnnotation(LayerAnnotation.REPOSITORY);
        } else if (annotations.stream().anyMatch(CONTROLLER_ANNOTATIONS::contains)) {
            instance.setLayerAnnotation(LayerAnnotation.CONTROLLER);
        } else if (isSpringDataRepositoryInterface(instance)) {
            instance.setLayerAnnotation(LayerAnnotation.REPOSITORY);
        } else if (annotations.stream().anyMatch(TESTING_CLASS_ANNOTATIONS::contains)
                || hasDirectTestMethod(declaration)) {
            instance.setLayerAnnotation(LayerAnnotation.TESTING);
        } else {
            instance.setLayerAnnotation(LayerAnnotation.OTHER);
        }
    }

    private boolean isSpringDataRepositoryInterface(ClassInstance instance) {
        if (instance.getJavaFileType() != JavaFileType.INTERFACE || instance.getSuperClasses() == null) {
            return false;
        }
        return instance.getSuperClasses().stream()
                .anyMatch(SPRING_DATA_REPOSITORY_INTERFACES::contains);
    }

    private boolean hasDirectTestMethod(TypeDeclaration<?> declaration) {
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof MethodDeclaration method) {
                boolean isTestMethod = method.getAnnotations().stream()
                        .map(AnnotationExpr::getNameAsString)
                        .anyMatch(TESTING_METHOD_ANNOTATIONS::contains);
                if (isTestMethod) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setInheritance(TypeDeclaration<?> declaration, ClassInstance instance) {
        if (declaration instanceof ClassOrInterfaceDeclaration classDecl) {
            instance.setSuperClasses(
                    classDecl.getExtendedTypes().stream()
                            .map(NodeWithSimpleName::getNameAsString)
                            .toList());
        } else {
            instance.setSuperClasses(List.of());
        }
        instance.setImplementedInterfaces(interfaceService.getImplementedInterfaces(declaration));
    }

    private void setMembers(TypeDeclaration<?> declaration, ClassInstance instance) {
        // Extract class variables FIRST so MethodService receives the exact map of instance fields
        List<VariableInstance> classVariables = variableService.getVariables(declaration);
        instance.setClassVariables(classVariables);

        Map<String, String> instanceFieldsByName = new LinkedHashMap<>();
        if (classVariables != null) {
            for (VariableInstance var : classVariables) {
                if ("instance".equals(var.getScope()) && var.getName() != null) {
                    instanceFieldsByName.put(var.getName(), var.getType());
                }
            }
        }

        instance.setMethods(methodService.getMethods(declaration, instanceFieldsByName, declaration.getNameAsString()));
        instance.setStatements(statementService.getStatements(declaration));
        instance.setConstructors(constructorService.getConstructors(declaration));
    }

    private void setInnerClasses(TypeDeclaration<?> declaration, ClassInstance instance, String packageName) {
        List<ClassInstance> innerClasses = new ArrayList<>();
        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof TypeDeclaration<?> inner) {
                innerClasses.add(parseType(inner, packageName, instance.getQualifiedName()));
            }
        }
        instance.setInnerClasses(innerClasses);
    }

    private void setExceptionHandling(TypeDeclaration<?> declaration, ClassInstance instance) {
        instance.setExceptionHandling(exceptionHandlerService.getExceptionHandling(declaration));
    }

    private void setLinesOfCode(TypeDeclaration<?> declaration, ClassInstance instance) {
        int linesOfCode = declaration.getRange()
                .map(range -> range.end.line - range.begin.line + 1)
                .orElse(0);
        instance.setLinesOfCode(linesOfCode);
    }

    private List<String> collectUsedClasses(TypeDeclaration<?> declaration) {
        Set<String> usedClasses = new LinkedHashSet<>();
        UsedClassCollector collector = new UsedClassCollector(declaration, usedClasses);
        declaration.accept(collector, null);
        return new ArrayList<>(usedClasses);
    }

    private static final class UsedClassCollector extends VoidVisitorAdapter<Void> {

        private final TypeDeclaration<?> root;
        private final Set<String> usedClasses;

        private UsedClassCollector(TypeDeclaration<?> root, Set<String> usedClasses) {
            this.root = root;
            this.usedClasses = usedClasses;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            if (n != root) {
                return;
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(RecordDeclaration n, Void arg) {
            if (n != root) {
                return;
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(EnumDeclaration n, Void arg) {
            if (n != root) {
                return;
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(AnnotationDeclaration n, Void arg) {
            if (n != root) {
                return;
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            boolean isScopeOfParentType = n.getParentNode()
                    .filter(ClassOrInterfaceType.class::isInstance)
                    .map(ClassOrInterfaceType.class::cast)
                    .flatMap(ClassOrInterfaceType::getScope)
                    .map(scope -> scope == n)
                    .orElse(false);

            if (!isScopeOfParentType) {
                usedClasses.add(n.getNameWithScope());
            }
            // Descend into scope so generic arguments on Outer<Foo>.Inner are collected
            // (isScopeOfParentType prevents emitting raw package/qualifier fragments)
            n.getScope().ifPresent(s -> s.accept(this, arg));
            // Always descend into type arguments (handles nested generics and JpaRepository<User, Long>)
            n.getTypeArguments().ifPresent(args -> args.forEach(t -> t.accept(this, arg)));
            n.getAnnotations().forEach(a -> a.accept(this, arg));
        }

        @Override
        public void visit(MarkerAnnotationExpr n, Void arg) {
            usedClasses.add(n.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(SingleMemberAnnotationExpr n, Void arg) {
            usedClasses.add(n.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(NormalAnnotationExpr n, Void arg) {
            usedClasses.add(n.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            n.getScope().ifPresent(this::collectStaticTypeScope);
            super.visit(n, arg);
        }

        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            collectStaticTypeScope(n.getScope());
            super.visit(n, arg);
        }

        private void collectStaticTypeScope(Expression scope) {
            if (scope instanceof NameExpr nameExpr) {
                String name = nameExpr.getNameAsString();
                if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                    usedClasses.add(name);
                }
            } else if (scope instanceof FieldAccessExpr fieldAccess) {
                String terminal = fieldAccess.getNameAsString();
                if (!terminal.isEmpty() && Character.isUpperCase(terminal.charAt(0)) && isPureNameChain(fieldAccess)) {
                    usedClasses.add(fieldAccess.toString());
                }
            }
        }

        private boolean isPureNameChain(Expression expr) {
            Expression current = expr;
            while (current instanceof FieldAccessExpr fa) {
                current = fa.getScope();
            }
            return current instanceof NameExpr;
        }
    }
}
