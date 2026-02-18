# Backend Service

Java Spring Boot 3.3 monolith providing REST APIs for authentication, user management, RBAC, face recognition orchestration, and audit logging.

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 LTS | Runtime |
| Spring Boot | 3.3.4 | Application framework |
| Spring Security 6 | JWT | Authentication & method-level authorization |
| Spring Data JPA | HikariCP | MySQL ORM & connection pooling |
| Spring Data Redis | - | Token blacklist & caching |
| Spring AMQP | - | RabbitMQ audit event pipeline |
| JJWT | 0.12.5 | JWT token handling |
| MinIO SDK | 8.5.7 | S3-compatible object storage |
| OkHttp | 4.12.0 | HTTP client for AI service |
| Resilience4j | 2.2.0 | Retry & circuit breaker |
| Liquibase | - | Database migrations |
| Gradle | 8.5 | Build tool |

## Quick Start

### Prerequisites
- Java 17+
- MySQL 8.0, Redis 7.4, RabbitMQ (or use docker-compose from root)

### Run

```bash
# Start infrastructure
cd .. && docker-compose up -d mysql redis rabbitmq minio

# Run backend
./gradlew bootRun

# Health check
curl http://localhost:8080/ping
```

### Build

```bash
./gradlew compileJava     # Compile
./gradlew test            # Unit + integration tests
./gradlew bootJar         # Build deployable JAR
```

## Project Structure

Modular monolith — code is organized by feature, not by layer:

```
src/main/java/com/mario/backend/
├── auth/        # Authentication, JWT, password reset, invitation
├── users/       # User profiles, admin user management
├── rbac/        # Role & permission management
├── face/        # Face recognition (calls Python AI service)
├── audit/       # Audit logging pipeline (RabbitMQ → MySQL/ES)
├── gateway/     # Security config, infrastructure beans, filters
├── common/      # Shared DTOs, exceptions, HTTP client
└── logging/     # Distributed tracing, log masking
```

Each module contains: `controller/`, `service/`, `repository/`, `entity/`, `dto/`

## API Endpoints

### Public
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/user/authenticate` | Login |
| POST | `/api/v1/user/register` | Register |
| POST | `/api/v1/user/refresh` | Refresh token |
| POST | `/api/v1/user/logout` | Logout |
| POST | `/api/v1/user/forgot-password` | Request password reset email |
| POST | `/api/v1/user/reset-password` | Reset password with token |
| POST | `/api/v1/user/accept-invitation` | Accept invitation & set password |

### Protected (JWT Required)
| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| POST | `/api/v1/profile` | `user:read_self` | Get profile |
| PUT | `/api/v1/profile` | `user:update_self` | Update profile |
| PUT | `/api/v1/user/change-password` | `user:update_self` | Change password |
| POST | `/api/v1/face/register-identity` | `face:register` | Register face |
| POST | `/api/v1/face/recognize-identity` | `face:recognize` | Recognize face |
| POST | `/api/v1/face/delete-identity` | `face:delete` | Delete face data |
| GET | `/api/v1/face/is-registered` | `face:check` | Check registration |
| GET | `/api/v1/audit/all` | `audit:read_all` | All audit logs |
| GET | `/api/v1/audit/user/{id}` | `audit:read_all` or own | User audit logs |

### Admin (SUPERADMIN)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/users` | List users (paginated) |
| GET | `/api/v1/admin/users/{id}` | Get user |
| PUT | `/api/v1/admin/users/{id}` | Update user |
| PUT | `/api/v1/admin/users/{id}/status` | Activate/deactivate/ban |
| POST | `/api/v1/admin/users/invite` | Invite user via email |
| GET | `/api/v1/admin/rbac/roles` | List roles |
| POST | `/api/v1/admin/rbac/roles` | Create role |
| PUT | `/api/v1/admin/rbac/roles/{id}` | Update role |
| DELETE | `/api/v1/admin/rbac/roles/{id}` | Delete role |
| GET | `/api/v1/admin/rbac/permissions` | List permissions |
| PUT | `/api/v1/admin/rbac/roles/{id}/permissions` | Set role permissions |
| PUT | `/api/v1/admin/rbac/users/{id}/role` | Assign role to user |

## Configuration

Key environment variables (see `application.yml` for all):

```bash
DB_HOST=localhost           DB_PORT=3306
DB_NAME=backend_db          DB_USERNAME=root        DB_PASSWORD=...
REDIS_HOST=localhost        REDIS_PORT=6379
RABBITMQ_HOST=localhost     RABBITMQ_PORT=5672
JWT_SECRET=<base64-256bit>
MINIO_ENDPOINT=http://localhost:9000
FACE_SERVICE_URL=http://localhost:5000
FRONTEND_URL=http://localhost
MAIL_HOST=smtp.gmail.com    MAIL_USERNAME=...       MAIL_PASSWORD=...
```

## Database Migrations

Managed by Liquibase. Migration files in `db/migration/mysql/`:

```bash
# Apply migrations (dev)
cd db/migration/mysql/dev
liquibase update
```

See `db/migration/mysql/README.md` for full migration docs.

## Testing

```bash
./gradlew test          # Unit + integration (Testcontainers)
./gradlew e2eTest       # E2E (requires running docker-compose stack)
```

Coverage enforced by JaCoCo: 30% global minimum, 50% for auth modules.

## Docker

```bash
docker build -t backend-service .
docker run -p 8080:8080 backend-service
```

Multi-stage build: Gradle builder → Eclipse Temurin 17 JRE Alpine. Runs as non-root user with built-in healthcheck.
