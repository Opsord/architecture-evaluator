# AGENTS.md

This is the **parent** repository for Architecture Evaluator. It wires two git submodules and Docker Compose. Almost all application code lives in the submodules, not here.

| Path | Remote | Nested app directory |
|------|--------|----------------------|
| `backend/` | https://github.com/Opsord/architecture-evaluator-backend | `backend/architecture_evaluator_backend/` |
| `frontend/` | https://github.com/Opsord/architecture-evaluator-frontend | `frontend/architecture-evaluator-frontend/` |

Prefer implementing features **in those repos** (or in the submodule checkouts) rather than adding app code at this root. After a submodule `main` moves, update the gitlink commits on this repo in a dedicated PR.

Each submodule has its own `AGENTS.md` with stack-specific rules. Follow the closest file when editing backend or frontend sources.

## Setup and commands

```bash
git clone --recurse-submodules https://github.com/Opsord/architecture-evaluator.git
cd architecture-evaluator
# if you already cloned without submodules:
git submodule update --init --recursive
```

**Docker Compose** (from this root):

```bash
docker compose up --build
```

- Frontend: http://localhost (port 80) — Nginx proxies `/api/` to `backend:8080`
- Backend: http://localhost:8080

**Local dev** (no Compose):

```bash
cd backend/architecture_evaluator_backend && ./mvnw spring-boot:run
cd frontend/architecture-evaluator-frontend && npm install && npm run dev
```

UI: http://localhost:5173 (proxies `/api/orchestrator` to 8080).

There is no root `package.json` or root `pom.xml`. Do not run `mvn` / `npm` from the parent root.

## What this repo owns

- `.gitmodules`
- `docker-compose.yml` — build contexts are the nested app folders, not the submodule roots
- Root `README.md`, `LICENSE`, this file

Compose notes:

- `REACT_APP_API_URL` is unused (frontend is Vite; the browser uses same-origin `/api/`).
- Backend Dockerfile and `mvnw` live under `backend/architecture_evaluator_backend/`.
- Frontend Dockerfile builds Nginx with `/api/` → `http://backend:8080`.

## Submodule pointer workflow

1. Merge the change on the submodule’s `main`.
2. In this repo, on a branch:

   ```bash
   git submodule update --init
   git -C backend fetch origin && git -C backend checkout origin/main
   git -C frontend fetch origin && git -C frontend checkout origin/main
   git add backend frontend
   git commit -m "Point submodules at latest main."
   ```

3. Open a parent PR that only (or mainly) updates gitlinks.

Never commit a submodule pointing at an unpushed commit.

## Boundaries

- Do not vendor copies of the frontend/backend outside the submodules.
- Do not document fake clone URLs or APIs that are not implemented in the child repos.
- Root `.gitignore` is still a GitHub Pages/Jekyll template; it does not ignore Java/Node build outputs (those are ignored inside the submodules). Leave it unless you are asked to clean it up.
