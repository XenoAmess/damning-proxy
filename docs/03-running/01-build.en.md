[中文版](01-build.md)

# 01 Build Methods

> Last updated: 2026-06-17  
> Source version: current workspace

## Environment Requirements

| Tool | Version | Note |
|---|---|---|
| JDK | 21 | Required, maven-enforcer enforces ``src/main/resources/application.properties:2`` |

---

## Build JAR

```bash
mvn clean package
```

Artifacts:

```text
target/
├── damning-proxy-0.1.0.jar          # normal JAR, not directly runnable
└── quarkus-app/
    └── quarkus-run.jar                     # Quarkus runnable JAR
```

Run:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

During the build `frontend-maven-plugin` will automatically:

1. Install Node.js and npm in `admin-web/`.
2. Run `npm install`.
3. Run `npm run build`.
4. Output the build artifacts to `src/main/resources/META-INF/resources/admin/`.

Frontend build config: `pom.xml:138`

---

## Build Native Image

Requires GraalVM 21; confirm that the normal JAR builds first.

```bash
mvn clean package -DskipTests
mvn clean package -Pnative
```

Or combined:

```bash
mvn clean package -Pnative -DskipTests
```

Artifact:

```text
target/damning-proxy-0.1.0-runner
```

Run:

```bash
./target/damning-proxy-0.1.0-runner
```

Notes:

- Native Image builds take a long time.
- Some dependencies (e.g. Groovy/JS engines) may require additional GraalVM reflection/resource configuration.
- `pom.xml` already configures `quarkus.native.additional-build-args=-H:+ReportExceptionStackTraces` to help troubleshoot.

`pom.xml:229`

---

## Frontend Standalone Build

To develop the frontend separately:

```bash
cd admin-web
npm install
npm run dev
```

The dev server proxies `/api` to `http://localhost:12360` by default; the backend must be running.

Production build:

```bash
cd admin-web
npm install
npm run build
```

Output goes to `admin-web/dist/`; Maven build will automatically copy it to `src/main/resources/META-INF/resources/admin/`.

`admin-web/package.json:6`

---

## Skip Frontend Build

To skip the frontend step (e.g. for pure backend debugging):

```bash
mvn clean package -DskipFrontend
```

However, `frontend-maven-plugin` currently has no skip property configured; you can achieve this by commenting the relevant execution in `pom.xml`. Future improvement.

---

## Build Command Quick Reference

| Scenario | Command |
|---|---|
| Dev mode | `mvn quarkus:dev` |
| Build runnable JAR | `mvn clean package` |
| Run JAR | `java -jar target/quarkus-app/quarkus-run.jar` |
| Build Native Image | `mvn clean package -Pnative` |
| Run Native | `./target/damning-proxy-0.1.0-runner` |
| Frontend standalone dev | `cd admin-web && npm run dev` |
| Frontend standalone build | `cd admin-web && npm run build` |
| Run tests | `mvn test` |

---

## Common Build Issues

| Issue | Possible Cause | Solution |
|---|---|---|
| `Java 21 is required` | JDK version mismatch | Use JDK 21 |
| Frontend build slow/fails | Node/npm download slow | Configure Maven/Node mirror, or run `npm install` manually in `admin-web/` |
| Native build fails | Missing GraalVM | Install GraalVM 21 and set `JAVA_HOME` |
