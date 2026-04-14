# StackSage

AI-powered Java debugging platform. Upload Java log files or submit pre-parsed exception data, and get automated root cause analysis, explanations, and actionable fixes powered by AI (via OpenRouter).

## Project Structure

```
stacksage/
├── stacksage-common/    # Shared parser library (no Spring dependency)
│   └── parser/          # ExceptionDetail, LogParser, RegexLogParser
├── stacksage-server/    # Spring Boot REST API server
│   ├── config/          # Async, AI provider, rate limiting, storage, Swagger
│   ├── controller/      # Upload + Analysis REST endpoints
│   ├── service/         # File upload, log analysis, AI diagnosis, orchestration
│   ├── model/           # JPA entities, DTOs, enums
│   └── repository/      # Spring Data JPA repositories
└── stacksage-cli/       # Standalone CLI tool (fat JAR)
    └── cli/             # StackSageCli main class
```

## Prerequisites

- **Java 17** (JDK)
- **PostgreSQL** (or a hosted instance like [Neon](https://neon.tech))
- **Maven** (wrapper included: `mvnw` / `mvnw.cmd`)
- **AI API key** (OpenRouter or OpenAI-compatible provider, for AI-powered diagnosis)

## Build

```bash
# Using the build script (sets JAVA_HOME automatically)
./build.ps1 package

# Or using Maven directly
./mvnw clean package
```

This builds all three modules:
- `stacksage-common-0.1.0-SNAPSHOT.jar` — parser library
- `stacksage-server-0.1.0-SNAPSHOT.jar` — Spring Boot executable JAR
- `stacksage-cli-0.1.0-SNAPSHOT.jar` — standalone fat JAR

## Run the Server

```bash
# Set required environment variables
export AI_API_KEY=sk-your-key-here
export DB_USERNAME=your_db_user       # optional, defaults to neondb_owner
export DB_PASSWORD=your_db_password   # optional, defaults to configured value

# Run the server
java -jar stacksage-server/target/stacksage-server-0.1.0-SNAPSHOT.jar
```

The server starts on port `8080` by default.

## API Documentation

Once the server is running, interactive API docs are available at:

- **Swagger UI:** `/swagger-ui.html`
- **OpenAPI JSON:** `/v3/api-docs`

## API Endpoints

### Uploads

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/uploads` | Upload a `.log` or `.txt` file (multipart) |
| `GET` | `/api/v1/uploads/{id}` | Get upload metadata (add `?content=true` for file content) |
| `DELETE` | `/api/v1/uploads/{id}` | Delete an upload |

### Analysis

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/uploads/{uploadId}/analysis` | Poll analysis results by upload ID |
| `POST` | `/api/v1/analyses` | Submit pre-parsed exceptions (used by CLI) |
| `GET` | `/api/v1/analyses/{analysisId}` | Get analysis results by analysis ID |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/actuator/health` | Application health check |
| `GET` | `/actuator/info` | Application info |

## CLI Usage

The CLI parses log files locally and sends only structured exception data to the server — ideal for large files.

```bash
# Basic usage (submits to localhost:8080)
java -jar stacksage-cli-0.1.0-SNAPSHOT.jar app.log

# Specify server URL
java -jar stacksage-cli-0.1.0-SNAPSHOT.jar --server http://myserver:8080 app.log

# Wait for results and print them
java -jar stacksage-cli-0.1.0-SNAPSHOT.jar --wait app.log

# Full example
java -jar stacksage-cli-0.1.0-SNAPSHOT.jar --server http://prod:8080 --wait /var/log/app.log
```

### Server URL Resolution

The CLI resolves the server URL in this order:

1. `--server <url>` flag (highest priority)
2. `STACKSAGE_SERVER` environment variable
3. `http://localhost:8080` (default)

### Output

```
Parsing app.log (2 MB)...
Found 3 exception(s)
Submitting to StackSage server (http://<server>:8080)...

Analysis ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
View results: http://<server>:8080/api/v1/analyses/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

With `--wait`, the CLI polls until analysis completes and prints formatted results:

```
Waiting for analysis...... COMPLETED

[1/3] java.lang.NullPointerException — HIGH
  Root cause: Uninitialized userService field
  Explanation: The @Autowired annotation is missing...
  Fix: Add @Autowired to the userService field...
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_USERNAME` | PostgreSQL username | `neondb_owner` |
| `DB_PASSWORD` | PostgreSQL password | (configured) |
| `AI_API_KEY` | AI provider API key (OpenRouter, OpenAI, etc.) | (none — required for AI features) |
| `STACKSAGE_SERVER` | CLI server URL | `http://localhost:8080` |

### Application Properties

Key settings in `application.yml`:

| Property | Description | Default |
|----------|-------------|---------|
| `app.upload-dir` | File storage directory | `./uploads` |
| `app.rate-limit.enabled` | Enable rate limiting | `true` |
| `app.rate-limit.max-requests` | Requests per window | `20` |
| `app.rate-limit.window-seconds` | Rate limit window | `60` |
| `app.ai.model` | AI model identifier | `meta-llama/llama-3.3-70b-instruct:free` |
| `app.ai.base-url` | AI provider base URL | `https://openrouter.ai/api` |
| `app.ai.max-tokens` | Max response tokens | `1024` |
| `app.ai.temperature` | AI temperature | `0.3` |

## Database

StackSage uses **PostgreSQL** with **Flyway** for schema migrations. Migrations are located in:

```
stacksage-server/src/main/resources/db/migration/
```

Flyway runs automatically on server startup and applies any pending migrations.

## Tech Stack

- **Java 17** + **Spring Boot 3.3**
- **PostgreSQL** + **Flyway** migrations
- **OpenRouter API** (Llama 3.3 70B free tier) via Spring RestClient — also compatible with OpenAI and other providers
- **Spring Boot Actuator** for health checks
- **SpringDoc OpenAPI** for interactive API documentation
- **Lombok** for reducing boilerplate
- **JUnit 5** + **AssertJ** for testing
