# Backend Service - Claude Code Guide

## Overview

Java 17 + Spring Boot 3.3.4 modular monolith. REST API for auth, user management, RBAC, face recognition orchestration, and audit logging.

## Build & Run

```bash
./gradlew bootRun                     # Dev server (port 8080)
./gradlew compileJava                 # Compile check
./gradlew test                        # Unit + integration tests
./gradlew e2eTest                     # E2E tests (needs docker-compose)
./gradlew jacocoTestReport            # Coverage report
```

Health check: `GET /ping` → `{"data":"pong"}`

## Architecture: Modular Monolith (Package-by-Feature)

Each module is self-contained with its own controller/service/repository/entity/dto packages:

```
src/main/java/com/mario/backend/
├── auth/           # JWT authentication, login, register, password reset, invitation
├── users/          # User profiles, admin user management
├── rbac/           # Roles & permissions CRUD (SUPERADMIN only)
├── face/           # Face register/recognize/delete (calls Python AI service)
├── audit/          # Audit logging → RabbitMQ → MySQL + Elasticsearch
├── gateway/        # SecurityConfig, filters, health, MinIO/Redis/OkHttp config
├── common/         # ApiResponse, ErrorCode, ApiException, GlobalExceptionHandler
└── logging/        # @Traceable AOP, TraceContext MDC, SensitiveDataMasker
```

**Module rules:**
- Modules communicate via service interfaces, not direct repository access
- Cross-module imports go through services only
- Shared code lives in `common/` and `logging/`

## Key Files

| Purpose | Path |
|---------|------|
| Security filter chain | `gateway/config/SecurityConfig.java` |
| JWT filter | `auth/security/JwtAuthenticationFilter.java` |
| JWT provider | `auth/security/JwtTokenProvider.java` |
| Auth business logic | `auth/service/AuthService.java` |
| Token blacklist (Redis) | `auth/service/TokenBlacklistService.java` |
| User business logic | `users/service/UserService.java` |
| RBAC service | `rbac/service/RbacService.java` |
| Face orchestration | `face/service/FaceService.java` |
| Audit event pipeline | `audit/publisher/AuditEventPublisher.java` |
| Audit HTTP filter | `gateway/filter/AuditLoggingFilter.java` |
| HTTP action → audit mapping | `audit/event/AuditActionMapper.java` |
| Error codes enum | `common/exception/ErrorCode.java` |
| Global exception handler | `common/exception/GlobalExceptionHandler.java` |
| Application config | `src/main/resources/application.yml` |

## Auth & Security

**JWT flow:** Login → access token (1h, has role+permissions claims) + refresh token (7d, no role). `JwtAuthenticationFilter` extracts claims → builds `AuthenticatedUser` principal → sets `GrantedAuthority` list.

**Authorization:** `@PreAuthorize("hasAuthority('face:register')")` on controller methods. RBAC admin endpoints use `@PreAuthorize("hasRole('SUPERADMIN')")`.

**Token blacklist:** Individual tokens via `blacklist:token:{jwt}` and user-level via `blacklist:user:{id}` (timestamp-based, checked against token issuedAt).

**Password hashing:** `BCrypt.gensalt()` + `BCrypt.hashpw()`. Salt stored separately in `auths` table.

**User status enforcement:** `login()` and `refreshToken()` check user status. Deactivated/banned/invited users are rejected. Status change triggers `blacklistUser()` to invalidate all existing tokens immediately.

## RBAC Model

```
Permission (service:action) ←→ role_permissions ←→ Role ←→ User
```

Permissions: `face:register`, `face:recognize`, `user:read_self`, `user:update_any`, `audit:read_all`, `rbac:manage_roles`, etc.

Default roles: SUPERADMIN (all 15 perms), PREMIUM_USER (7), BASIC_USER (4).

Role changes take effect on next token refresh (refresh endpoint reloads from DB).

## Audit Pipeline

```
HTTP Request → AuditLoggingFilter → AuditEventPublisher (RabbitMQ)
  → AuditLogConsumer (MySQL)
  → AuditSearchConsumer (Elasticsearch)
  → AlertConsumer (email alerts for critical events)
```

`AuditActionMapper` maps HTTP method+path to semantic actions (e.g., `POST /api/v1/user/authenticate` → `auth:login`). Add new mappings there when adding endpoints.

The `AuditLoggingFilter` reads actor info from request attribute `_audit_user` (set by `JwtAuthenticationFilter`), NOT from SecurityContext (which gets cleared before the outermost filter's finally block).

## Database Migrations

Uses **Liquibase** with environment-specific changelogs:

```
db/migration/mysql/
├── common/                    # Shared SQL migration files
├── dev/masterChangeLog.xml    # Dev changelog
├── staging/masterChangeLog.xml
└── prod/masterChangeLog.xml
```

JPA `ddl-auto: none` — Liquibase manages all schema changes. To add a migration:
1. Create SQL file in `common/` with naming: `YYYY.MM.DD_NN__description.sql`
2. Add `<changeSet>` entry to all 3 `masterChangeLog.xml` files

## Email Templates

FreeMarker templates at `src/main/resources/email-templates/`. The `email-service` library (local JAR in `libs/`) provides `EmailService.send(EmailRequest)`. Templates are loaded from the consuming app's classpath, so new templates go here — no need to rebuild the email-service JAR.

Available templates: `password-reset`, `invitation`, `welcome`, `error-alert`.

## External Services

| Service | Connection | Config key |
|---------|-----------|------------|
| Python AI service | HTTP via OkHttp + Resilience4j retry | `face-recognition.service-url` |
| MySQL | JDBC via HikariCP | `spring.datasource.*` |
| Redis | Spring Data Redis | `spring.data.redis.*` |
| MinIO | MinIO Java SDK | `minio.*` |
| RabbitMQ | Spring AMQP | `spring.rabbitmq.*` |
| Elasticsearch | Spring Data ES | `spring.elasticsearch.*` |
| SMTP | Spring Mail | `spring.mail.*` |

## Testing

```
src/test/java/
├── e2e/           # Full stack (needs docker-compose, excluded from ./gradlew test)
├── integration/   # Real DB via Testcontainers (MySQL + Redis)
├── unittest/      # Mocked dependencies
└── testutil/      # IntegrationTestBase, JwtTestHelper, TestDataFactory
```

Coverage: JaCoCo enforces 30% global, 50% for auth/security modules.

## Conventions

- DTOs use `@JsonProperty("snake_case")` for API, camelCase internally
- All inputs validated with Jakarta Validation (`@NotBlank`, `@Email`, `@Size`)
- Standard response: `ApiResponse<T>` → `{ data: T }` or `{ error: { code, message } }`
- API versioning: `/api/v1/...`
- Constructor injection via `@RequiredArgsConstructor` (no field `@Autowired`)
- Never expose entities directly — always map to response DTOs
