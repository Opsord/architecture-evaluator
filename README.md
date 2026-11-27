# Architecture Evaluator for Spring Boot Applications

## Description

This web application allows automatic evaluation of the architecture of Java applications based on the Spring Boot framework. It provides key maintainability-related metrics and offers an interactive visual interface to analyze the results without requiring advanced knowledge of metric evaluation.

## Main Features

1. **Automated Architectural Analysis:**

   - Evaluation of layered architectures in monolithic web applications.
   - Detection of architectural patterns and design principle violations.
   - Calculation of quality metrics such as maintainability, coupling, and cohesion.

2. **Interactive Visual Dashboard:**

   - Graphical presentation of key metrics in intuitive diagrams.
   - Enables interactive exploration of results.

3. **Basic Module for Architectural Quality Evaluation:**

   - Calculation of metrics such as cyclomatic complexity, coupling, and cohesion.
   - Automated evaluation of code maintainability.

4. **Modular and Extensible Design:**

   - Support for integrating new metrics and evaluation criteria.
   - Adaptability to different projects or research needs.

5. **Optimized Evaluation Process:**

   - Reduces complexity in analysis and generates results quickly.
   - Clear and structured presentation of metrics.

6. **Integration with Code Repositories:**

   - Connects with version control systems such as Git.
   - Allows evaluation of third-party projects stored in remote repositories.

## Scope and Limitations

- **Scope:**

  - Focused on evaluating monolithic applications developed in **Java** using **Spring Boot**.
  - Static code analysis, concentrating on software structure and design.

- **Limitations:**

  - Not compatible with other frameworks or languages such as Python or C#.
  - Does not analyze microservices, serverless, or hybrid architectures.
  - Does not include dynamic analysis such as runtime or load testing.
  - Performance may be affected in extremely large projects.

## Product Evaluation

The system's quality will be evaluated based on functional and non-functional criteria:

### Functional Aspects

- Evaluated through **user stories** with clear acceptance criteria.
- Example:
  - **Story:** As a user, I want to visualize maintainability metrics on an interactive dashboard.
  - **Acceptance Criteria:**
    - The dashboard must display metrics such as cyclomatic complexity and coupling.
    - The charts must support interactions such as filtering and zooming.
    - The system must provide visual feedback to the user.

### Non-Functional Aspects

- **Usability:** Measured through **System Usability Scale (SUS)** questionnaires.
- **Code Quality:**
  - Evaluated with tools like **SonarQube**.
  - Maintain technical debt ratio below 5%.
- **Performance:**
  - Load and stress tests to measure stability and resource efficiency.

## Installation and Usage

### Prerequisites

- **Java 21 LTS** (for local backend development)
- **Node.js 20+** and **pnpm 10+** (`corepack enable`, for local frontend development)
- **Docker** & **Docker Compose** (recommended for running the full stack)

### Quick Start with Docker Compose

```sh
docker compose up --build
```

Open `http://localhost` in your browser. Nginx serves the frontend SPA on port `80` and proxies `/api/` requests to the internal `backend:8080` service (port `8080` is not published to the host).

### Local Development (Monorepo)

1. Run the backend (from `backend/`):
   ```sh
   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```
2. Run the frontend (from `frontend/`):
   ```sh
   cd frontend
   pnpm install
   pnpm dev
   ```
3. Open the dev server at `http://localhost:5173`.

## License

This project is licensed under the MIT License. See the `LICENSE` file for more details.
