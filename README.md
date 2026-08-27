# Architecture Evaluator

Web app that **statically evaluates the architecture of Java / Spring Boot** projects. It computes maintainability-related metrics (size, cyclomatic complexity, coupling, cohesion) and shows them on an interactive **3D dashboard**.

This repository is the **composition root**: Docker Compose plus two git submodules.

| Submodule | Repository |
|-----------|------------|
| `backend/` | [architecture-evaluator-backend](https://github.com/Opsord/architecture-evaluator-backend) |
| `frontend/` | [architecture-evaluator-frontend](https://github.com/Opsord/architecture-evaluator-frontend) |

## Features

- Analyze a **zip upload** or a **public GitHub repository** (`main` branch).
- Classify types by typical Spring layers (controllers, services, repositories, entities, …).
- Metrics per class: LOC, methods, statements, approximate McCabe CC, Ca/Ce/instability, LCOM1–5.
- Explore classes as cubes in a layered 3D scene (no metric expertise required).

## Scope and limitations

**In scope:** static analysis of monolithic Java / Spring Boot codebases.

**Out of scope:** other languages/frameworks; microservices topology; runtime or load testing. Very large projects can take minutes and produce large JSON responses.

GitHub ingest downloads `https://github.com/{owner}/{repo}/archive/refs/heads/main.zip` — repositories whose default branch is not `main` will fail.

## Prerequisites

- **Docker** and Docker Compose, **or**
- **JDK 17+** and **Node.js 18+** (20 recommended) for running the submodules directly

## Quick start (Docker)

```sh
git clone --recurse-submodules https://github.com/Opsord/architecture-evaluator.git
cd architecture-evaluator
docker compose up --build
```

If you cloned without `--recurse-submodules`:

```sh
git submodule update --init --recursive
```

Then open **http://localhost** (frontend on port 80). The backend is on **http://localhost:8080**. The Nginx image proxies `/api/` to the backend service.

Compose build contexts are the nested app folders:

- `backend/architecture_evaluator_backend`
- `frontend/architecture-evaluator-frontend`

## Local development (without Docker)

Terminal 1 — API:

```sh
cd backend/architecture_evaluator_backend
./mvnw spring-boot:run
```

Terminal 2 — UI:

```sh
cd frontend/architecture-evaluator-frontend
npm install
npm run dev
```

Open **http://localhost:5173**. Vite proxies `/api/orchestrator` to `http://localhost:8080`.

There is no Maven or npm project at this repository root.

## How analysis works

1. Frontend `POST`s to `/api/orchestrator/analyze-upload` or `/analyze-github`.
2. Backend unpacks the project, prefers `src/main/java`, parses sources, computes metrics.
3. Frontend stores the JSON in memory and opens the 3D dashboard.

Details, endpoints, and tests: see the submodule READMEs.

## License

MIT. See [LICENSE](LICENSE). Submodules use the same license.
