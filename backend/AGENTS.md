# AGENTS.md

This directory is the **backend** of Architecture Evaluator in the monorepo. It statically analyzes Java / Spring Boot projects and returns maintainability metrics as JSON.

## Setup and commands

Run from `backend/`:

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw -B verify
./mvnw -B verify -Pcoverage
```

Java 21 LTS. Spring Boot 3.4.x. Default port **8080**. There is **no database** at runtime (`spring-boot-starter-data-jpa` is not used). RFC 7807 `ProblemDetail` is enabled globally via `GlobalExceptionHandler`.

Do not run Maven from the git root.

## Architecture

Package root: `io.github.opsord.architecture_evaluator_backend`.

| Area | Role |
|------|------|
| `modules.api.orchestrator` | HTTP API, zip/GitHub ingest, analysis pipeline |
| `modules.api.project_scanner` | Find `src`, parse `pom.xml` / Gradle files |
| `modules.api.parser` | JavaParser visitors → class/method/statement models |
| `modules.api.processor` | Metrics (program, complexity, coupling, cohesion) |
| `modules.config.WebConfig` | CORS |

Only controller: `OrchestratorController` at `/api/orchestrator`.

- `POST /analyze?projectPath=` — local path (disabled by default with `403 Forbidden`; enable with `archev.local-analysis.enabled=true` or `--spring.profiles.active=dev`)
- `POST /analyze-upload` — multipart field `project` (`.zip` or `.tar` archive up to 150 MB)
- `POST /analyze-github?repoUrl=` — GitHub HTTPS URL; tries `main` branch first and falls back to `master` on HTTP 404

Response type: `ProjectAnalysisInstance` (classes grouped by layer: entities, documents, repositories, services, controllers, testClasses, otherClasses, plus pom/gradle DTOs).

Metrics per class: LOC/methods/statements, approx McCabe CC, afferent/efferent coupling + instability, LCOM1–5.

## Conventions

- Prefer existing visitor/service split: visitors extract AST data; services assemble DTOs and metrics.
- Keep JSON payloads small: omit nulls (`NON_NULL`), do **not** put full AST source on statements. For `MethodCallExpr` used by LCOM4, store the **callee method name** only (`StatementInstance`). `CohesionMetricsService.extractCalledNames` must stay null-safe.
- Dependency lookup must stay O(n) (`Set`/`Map`), not nested scans of all classes.
- JavaParser instances are `ThreadLocal` in `SrcScannerService` for parallel parse — do not share a parser across threads.
- Prefer `src/main/java` when present; zip extract should skip non-analysis paths.
- Tests live under `src/test/java/...` mirroring the module. Use JUnit 5 + Mockito. After behavior changes, update the matching `*Test` and run `./mvnw test`.
- Lombok is on the compiler annotation path; do not hand-write getters/setters on `@Data` types.

## Gotchas

- **LCOM4 depends on method names on statements.** Removing `structure`/source without keeping call names NPEs cohesion.
- GitHub ingest tries `main` first and falls back to `master` on HTTP 404.
- Uploads up to 150MB; analysis can take minutes. HTTP GitHub timeouts are 15s connect / 120s read.
- CORS origins are configurable via `archev.cors.allowed-origins` (defaults to Vite `5173`, CRA `3000`, and Docker frontend on port 80).
- SonarCloud runs in `.github/workflows/backend.yml` (`./mvnw -B verify ... sonar -Pcoverage`). Local work does not need Sonar. `SONAR_TOKEN_BACK` is a repo secret.

## Boundaries

- Do not add persistence or rewrite the pipeline as async jobs unless asked.
- Do not invent extra REST endpoints in the README or code without implementing them.
- Do not commit secrets, PostgreSQL credentials, or large sample zips.
