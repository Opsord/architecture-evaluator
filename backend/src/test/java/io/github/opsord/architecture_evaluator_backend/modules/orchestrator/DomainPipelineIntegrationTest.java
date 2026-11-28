package io.github.opsord.architecture_evaluator_backend.modules.orchestrator;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.instances.ProjectAnalysisInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services.OrchestratorService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.JavaFileType;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.LayerAnnotation;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.ClassService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.annotation.AnnotationService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.constructor.ConstructorService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.exception_handler.ExceptionHandlerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.interface_instance.InterfaceService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.method.MethodService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable.VariableService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.FileInstanceService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.package_part.PackageService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.ProcessedClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.parts.ImportCategory;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.ClassAnalysisService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.FileAnalysisService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts.CohesionMetricsService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts.ComplexityMetricsService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts.CouplingMetricsService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts.ImportClassifierService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts.ProgramMetricsService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.GradleScannerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.PomScannerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.ScannerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.SrcScannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainPipelineIntegrationTest {

    private OrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        StatementService statementService = new StatementService();
        MethodService methodService = new MethodService(statementService);
        VariableService variableService = new VariableService();
        ConstructorService constructorService = new ConstructorService();
        AnnotationService annotationService = new AnnotationService();
        InterfaceService interfaceService = new InterfaceService();
        ExceptionHandlerService exceptionHandlerService = new ExceptionHandlerService();
        ClassService classService = new ClassService(
                methodService, statementService, variableService,
                constructorService, annotationService, interfaceService, exceptionHandlerService);
        PackageService packageService = new PackageService();
        FileInstanceService fileInstanceService = new FileInstanceService(classService, packageService, annotationService);

        ScannerService scannerService = new ScannerService();
        PomScannerService pomScannerService = new PomScannerService(scannerService);
        GradleScannerService gradleScannerService = new GradleScannerService();
        SrcScannerService srcScannerService = new SrcScannerService(fileInstanceService);

        ProgramMetricsService programMetricsService = new ProgramMetricsService(statementService);
        ComplexityMetricsService complexityMetricsService = new ComplexityMetricsService();
        CouplingMetricsService couplingMetricsService = new CouplingMetricsService();
        CohesionMetricsService cohesionMetricsService = new CohesionMetricsService();
        ClassAnalysisService classAnalysisService = new ClassAnalysisService(
                programMetricsService, complexityMetricsService, couplingMetricsService, cohesionMetricsService);
        ImportClassifierService importClassifierService = new ImportClassifierService();
        FileAnalysisService fileAnalysisService = new FileAnalysisService(classAnalysisService, importClassifierService);

        orchestratorService = new OrchestratorService(
                scannerService, pomScannerService, gradleScannerService,
                srcScannerService, fileAnalysisService, fileInstanceService);
    }

    @Test
    void endToEndJava21SpringBootAnalysisProducesAccurateDomainMetrics(@TempDir Path tempDir) throws IOException {
        write(tempDir.resolve("pom.xml"), """
                <project>
                  <groupId>com.acme.shop</groupId>
                  <artifactId>shop-service</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <java.version>21</java.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.managed.only</groupId>
                        <artifactId>managed-lib</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        Path base = tempDir.resolve("src/main/java/com/acme/shop");

        // 1. Entity User + nested enum Status
        write(base.resolve("entity/User.java"), """
                package com.acme.shop.entity;
                import jakarta.persistence.Entity;

                @Entity
                public class User {
                    private Long id;
                    private String name;

                    public enum Status { ACTIVE, INACTIVE }
                }
                """);

        // 2. Homonymous record dto.User
        write(base.resolve("dto/User.java"), """
                package com.acme.shop.dto;

                public record User(Long id, String name) {
                    public User {
                        if (id == null) {
                            throw new IllegalArgumentException("id required");
                        }
                    }
                }
                """);

        // 3. Static utility class + abstract base class
        write(base.resolve("util/NameUtils.java"), """
                package com.acme.shop.util;

                public class NameUtils {
                    public static String normalize(String s) {
                        return s == null ? "" : s.trim();
                    }
                }
                """);

        write(base.resolve("service/BaseService.java"), """
                package com.acme.shop.service;

                public abstract class BaseService {
                    protected abstract String tag();
                }
                """);

        // 4. Repository interface referencing entity.User via generic type argument
        write(base.resolve("repo/UserRepository.java"), """
                package com.acme.shop.repo;
                import com.acme.shop.entity.User;
                import org.springframework.stereotype.Repository;
                import java.util.List;

                @Repository
                public interface UserRepository extends JpaRepository<User, Long> {
                    List<User> findAll();
                }
                """);

        // 4b. Unannotated Spring Data repository + custom exception
        write(base.resolve("repo/AuditRepository.java"), """
                package com.acme.shop.repo;
                import com.acme.shop.entity.User;
                import org.springframework.data.repository.ListCrudRepository;

                public interface AuditRepository extends ListCrudRepository<User, Long> {
                    long countByName(String name);
                }
                """);

        write(base.resolve("exception/ShopNotFoundException.java"), """
                package com.acme.shop.exception;

                public class ShopNotFoundException extends RuntimeException {
                    public ShopNotFoundException(String message) {
                        super(message);
                    }
                }
                """);

        // 5. Service using wildcard import, static import, nested type, static logger, static factory,
        //    real field accesses, lambda & for-each shadowing (for (String items : items)),
        //    this::helper (isolated LCOM4 link), switch expr + when guard, try/catch/throw
        write(base.resolve("service/UserService.java"), """
                package com.acme.shop.service;
                import com.acme.shop.entity.*;
                import com.acme.shop.repo.UserRepository;
                import static com.acme.shop.util.NameUtils.normalize;
                import org.springframework.stereotype.Service;
                import java.util.List;

                @Service
                public class UserService extends BaseService {
                    private static final String LOGGER = "UserService";
                    private final UserRepository userRepository;
                    private String prefix;
                    private List<String> items;

                    public UserService(UserRepository userRepository) {
                        this.userRepository = userRepository;
                    }

                    public static UserService create(UserRepository repo) {
                        return new UserService(repo);
                    }

                    @Override
                    protected String tag() {
                        return "user";
                    }

                    public List<String> listNames() {
                        List<User> users = userRepository.findAll();
                        for (String items : items) {
                            normalize(items);
                        }
                        items.forEach(prefix -> normalize(prefix));
                        return users.stream().map(this::formatUser).toList();
                    }

                    public String formatUser(User user) {
                        User.Status status = User.Status.ACTIVE;
                        Object input = status;
                        try {
                            return switch (input) {
                                case User.Status s when s == User.Status.ACTIVE -> prefix + tag();
                                case User.Status s -> {
                                    throw new IllegalStateException("inactive");
                                }
                                default -> "other";
                            };
                        } catch (IllegalStateException ex) {
                            return "fallback";
                        }
                    }

                    public com.acme.shop.dto.User toDto(User user) {
                        return new com.acme.shop.dto.User(1L, prefix);
                    }
                }
                """);

        ProjectAnalysisInstance result = orchestratorService.orchestrateProjectAnalysis(tempDir.toString());

        // POM verification
        assertNotNull(result.getPomFile());
        assertEquals("21", result.getPomFile().getJavaVersion());
        assertEquals(1, result.getPomFile().getDependencies().size());
        assertEquals("org.springframework.boot", result.getPomFile().getDependencies().get(0).getGroupId());

        // Homonymous User classes both preserved without collision
        assertEquals(1, result.getEntities().size());
        ProcessedClassInstance entityUser = result.getEntities().get(0);
        assertEquals("com.acme.shop.entity.User", entityUser.getClassInstance().getQualifiedName());
        assertEquals(1, entityUser.getClassInstance().getInnerClasses().size());
        assertEquals(List.of("jakarta.persistence.Entity"),
                entityUser.getClassAnalysisInstance().getClassifiedDependencies().get(ImportCategory.JAVA_STANDARD));

        ProcessedClassInstance dtoUser = result.getOtherClasses().stream()
                .filter(p -> "com.acme.shop.dto.User".equals(p.getClassInstance().getQualifiedName()))
                .findFirst()
                .orElseThrow();
        assertEquals(JavaFileType.RECORD, dtoUser.getClassInstance().getJavaFileType());
        assertNull(dtoUser.getClassAnalysisInstance().getCohesionMetrics());
        assertEquals(List.of("com.acme.shop.service.UserService"), dtoUser.getClassInstance().getDependentClasses());

        // Repositories: UserRepository (@Repository) + AuditRepository (Spring Data heuristic)
        assertEquals(2, result.getRepositories().size());
        ProcessedClassInstance userRepo = result.getRepositories().stream()
                .filter(p -> "com.acme.shop.repo.UserRepository".equals(p.getClassInstance().getQualifiedName()))
                .findFirst()
                .orElseThrow();
        assertEquals(LayerAnnotation.REPOSITORY, userRepo.getClassInstance().getLayerAnnotation());
        assertEquals(List.of("com.acme.shop.entity.User"), userRepo.getClassInstance().getClassDependencies());
        assertEquals(List.of("com.acme.shop.service.UserService"), userRepo.getClassInstance().getDependentClasses());
        assertNull(userRepo.getClassAnalysisInstance().getCohesionMetrics());
        assertEquals(0, userRepo.getClassAnalysisInstance().getComplexityMetrics().getApproxMcCabeCC());
        assertEquals(0, userRepo.getClassAnalysisInstance().getComplexityMetrics().getMaxMethodMcCabeCC());
        assertEquals(0.0, userRepo.getClassAnalysisInstance().getComplexityMetrics().getImprovedCC());

        ProcessedClassInstance auditRepo = result.getRepositories().stream()
                .filter(p -> "com.acme.shop.repo.AuditRepository".equals(p.getClassInstance().getQualifiedName()))
                .findFirst()
                .orElseThrow();
        assertEquals(LayerAnnotation.REPOSITORY, auditRepo.getClassInstance().getLayerAnnotation());
        assertEquals(JavaFileType.INTERFACE, auditRepo.getClassInstance().getJavaFileType());
        assertNull(auditRepo.getClassAnalysisInstance().getCohesionMetrics());
        assertEquals(0, auditRepo.getClassAnalysisInstance().getComplexityMetrics().getApproxMcCabeCC());

        // Custom exception detected as JavaFileType.EXCEPTION without cohesion
        ProcessedClassInstance customEx = result.getOtherClasses().stream()
                .filter(p -> "com.acme.shop.exception.ShopNotFoundException".equals(p.getClassInstance().getQualifiedName()))
                .findFirst()
                .orElseThrow();
        assertEquals(JavaFileType.EXCEPTION, customEx.getClassInstance().getJavaFileType());
        assertNull(customEx.getClassAnalysisInstance().getCohesionMetrics());

        // Abstract base class detected
        ProcessedClassInstance baseService = result.getOtherClasses().stream()
                .filter(p -> "com.acme.shop.service.BaseService".equals(p.getClassInstance().getQualifiedName()))
                .findFirst()
                .orElseThrow();
        assertEquals(JavaFileType.ABSTRACT_CLASS, baseService.getClassInstance().getJavaFileType());

        // Service verification: FQN dependencies, LCOM1-5, McCabe CC
        assertEquals(1, result.getServices().size());
        ProcessedClassInstance userService = result.getServices().get(0);
        List<String> serviceDeps = userService.getClassInstance().getClassDependencies();
        assertTrue(serviceDeps.contains("com.acme.shop.repo.UserRepository"));
        assertTrue(serviceDeps.contains("com.acme.shop.entity.User"));
        assertTrue(serviceDeps.contains("com.acme.shop.dto.User"));
        assertTrue(serviceDeps.contains("com.acme.shop.util.NameUtils"));
        assertTrue(serviceDeps.contains("com.acme.shop.service.BaseService"));
        assertFalse(serviceDeps.contains("com.acme.shop.service.UserService")); // no self-dependency

        // Cohesion:
        // - create() is static -> excluded
        // - listNames() accesses userRepository + items (lambda parameter "prefix" does NOT falsely access field prefix!)
        //   and calls this::formatUser
        // - formatUser() accesses prefix and calls tag()
        // - toDto() accesses prefix
        // - tag() accesses no fields
        // So LCOM3 = 3 disconnected components ({listNames}, {formatUser, toDto}, {tag}),
        // while LCOM4 = 1 connected component via this::formatUser and tag()!
        var cohesion = userService.getClassAnalysisInstance().getCohesionMetrics();
        assertEquals(3, cohesion.getLackOfCohesion3());
        assertEquals(1, cohesion.getLackOfCohesion4());

        // Complexity: formatUser has switch (2 case labels + 1 when guard = 3) + 1 catch + 1 throw (does not subtract) => CC = 1 + 3 + 1 = 5
        var formatUserMethod = userService.getClassInstance().getMethods().stream()
                .filter(m -> "formatUser".equals(m.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(5, formatUserMethod.getMethodMetrics().getMcCabeComplexity());
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
