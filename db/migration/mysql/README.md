# Database Migrations (Liquibase)

Liquibase is the **single source of truth** for the database schema. It runs standalone (CLI), not embedded in Spring Boot.

## Directory Structure

```
db/migration/mysql/
├── common/                                        # Shared migration SQL files
│   ├── 0.0.0__baseline.sql                        # Full initial schema + seed data
│   └── 2026.02.17_01__add_algorithm_reg_column.sql # First migration
├── dev/
│   ├── 0.0.0__baseline.mysql.sql    # Copy of common baseline
│   ├── liquibase.properties         # Dev DB connection
│   └── masterChangeLog.xml          # Dev changelog
├── staging/
│   ├── 0.0.0__baseline.mysql.sql
│   ├── liquibase.properties         # Staging DB connection (uses env vars)
│   └── masterChangeLog.xml
└── prod/
    ├── 0.0.0__baseline.mysql.sql
    ├── liquibase.properties          # Prod DB connection (uses env vars)
    └── masterChangeLog.xml
```

## Prerequisites

- Liquibase CLI installed (`brew install liquibase` or [download](https://www.liquibase.org/download))
- MySQL JDBC driver available (usually bundled with Liquibase, or set `classpath` in properties)
- Network access to the target database

## Development Environment (Fresh Database)

For a **brand new dev database** (e.g., after `docker compose down -v && docker compose up -d`):

```bash
cd backend-service/db/migration/mysql/dev

# 1. Run all migrations (baseline + all changesets)
liquibase update

# 2. Verify
liquibase status  # Should show "all changesets have been applied"
```

This will:
1. Create the `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK` tracking tables
2. Execute the baseline (creates all tables + seeds RBAC data)
3. Execute all subsequent migrations (e.g., add `algorithm_reg` column)

## Staging/Production Environment (Existing Database)

For a database that **already has the schema** (created by JPA `ddl-auto: update` or manual SQL):

```bash
cd backend-service/db/migration/mysql/prod  # or staging/

# STEP 1: CRITICAL - Mark existing changesets as already applied
# This tells Liquibase "the database already has these changes, don't run them again"
liquibase changelog-sync

# STEP 2: Verify sync was successful
liquibase status  # Should show "all changesets have been applied"

# STEP 3: Now future migrations can be applied normally
# (e.g., if algorithm_reg column doesn't exist yet)
liquibase update
```

**Why `changelog-sync` first?**
- The baseline changeset creates tables like `users`, `roles`, etc.
- These tables **already exist** in staging/prod
- Running `liquibase update` directly would fail with "table already exists"
- `changelog-sync` records that these changesets were "applied" without executing them
- After sync, only **new** changesets (not yet in DATABASECHANGELOG) will execute

## Adding New Migrations

When you need to make a schema change:

1. Create a SQL file in `common/` with naming convention: `YYYY.MM.DD_NN__description.sql`
   ```
   common/2026.03.15_01__add_confidence_column_to_face_features.sql
   ```

2. Add a `<changeSet>` entry to **each** environment's `masterChangeLog.xml`:
   ```xml
   <changeSet id="20260315-01" author="your.name">
     <sqlFile path="../common/2026.03.15_01__add_confidence_column_to_face_features.sql"/>
   </changeSet>
   ```

3. Run in each environment:
   ```bash
   cd backend-service/db/migration/mysql/dev   # or staging/ or prod/
   liquibase update
   ```

## Rolling Back (Emergencies Only)

```bash
# Rollback the last N changesets
liquibase rollback-count 1

# Rollback to a specific tag
liquibase rollback <tag>
```

**Warning**: Not all SQL changes are auto-reversible. Write explicit rollback SQL in changesets when needed.

## Security Best Practices

- **Never commit real credentials** to `liquibase.properties` for staging/prod
- Use environment variables or a secrets manager:
  ```properties
  # prod/liquibase.properties
  url=jdbc:mysql://${LIQUIBASE_DB_HOST}:${LIQUIBASE_DB_PORT}/backend_db
  username=${LIQUIBASE_DB_USERNAME}
  password=${LIQUIBASE_DB_PASSWORD}
  ```
- Or pass credentials via CLI flags:
  ```bash
  liquibase --url=jdbc:mysql://prod-host:3306/backend_db \
            --username=$DB_USER \
            --password=$DB_PASS \
            update
  ```
- **Review every changeset** before running on production
- **Always run `liquibase status`** before `liquibase update` to see what will execute
- **Take a database backup** before running migrations on production:
  ```bash
  mysqldump -h prod-host -u root -p backend_db > backup_$(date +%Y%m%d_%H%M%S).sql
  ```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Table already exists" on first run | Run `liquibase changelog-sync` first (see staging/prod section) |
| "Checksum mismatch" | Someone modified an already-applied SQL file. Never edit applied changesets -- create a new one |
| Lock stuck | `liquibase release-locks` |
| Want to see what will run | `liquibase status --verbose` |
| Want to preview SQL without executing | `liquibase update-sql` |
