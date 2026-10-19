---
name: db-change
description: Make a database schema change in this repo safely - new Flyway migration, matching JPA entity, keeping the reservation overlap exclusion constraint in sync with ReservationStatus.ACTIVE, and tests that prove the constraint. Use whenever a task adds or changes tables, columns, statuses, indexes or constraints.
---

# Database change

Flyway owns the schema and Hibernate only validates it (`spring.jpa.hibernate.ddl-auto=validate`). A mismatch between the entity and the schema stops the app from starting.

## Steps

1. **New migration, never an edit.** Add `backend/src/main/resources/db/migration/V{next}__{what}.sql`, one number above the highest existing one. Never modify an applied migration: databases that already ran it will fail Flyway's checksum.
2. **Write it for existing data.** The Compose volume and any deployed database already have rows. In practice:
   - A new `NOT NULL` column needs a `DEFAULT`.
   - Renames and type changes need a data migration in the same file.
   - New `CHECK` or unique rules must hold for the rows already there.
3. **Mirror it in the entity.**
   - Column names are the snake_case of the field names.
   - Enums are `@Enumerated(EnumType.STRING)` in a `VARCHAR`.
   - Times are `Instant` in `TIMESTAMPTZ`.
   - Immutable columns get `updatable = false`.
   - Update the request and response records in the feature's `*Dtos` class and their `from()` mappers.
4. **Keep the overlap protection consistent.** Do this whenever the change touches `reservations`, its statuses, or its times.
   - The exclusion constraint `reservations_no_overlap` has `WHERE (status IN (...))`. That list must equal `ReservationStatus.ACTIVE`, which the service's overlap query uses.
   - Change both together. Dropping and re-adding the constraint must happen in the same migration. The latest definition is in `V2__approval_series_audit.sql`.
   - Exclusion constraints need the `btree_gist` extension, which `V1` already creates.
5. **Indexes** for the new filters and foreign keys used in queries. Use a partial index (`WHERE col IS NOT NULL`) for sparse columns.
6. **Tests:**
   - Rules enforced by the database (constraints) get an integration test that **bypasses the service**. It should write through the repository with `saveAndFlush` and expect `DataIntegrityViolationException`. See `databaseConstraintBlocksOverlapEvenWhenServiceIsBypassed` and `databaseConstraintAlsoBlocksOverlappingUpdates` in `ApiIntegrationTest`.
   - Service-level rules get unit tests in the matching `*ServiceTest`.
7. **Audit:** if the change adds a new state change on reservations or resources, record it with `AuditService.record(...)` inside the same `@Transactional` method. Add an `AuditEntry.Action` value if needed; it is stored as a string, so no migration is required for new enum values.
8. **Verify** with the `verify-change` skill. Make sure the migration also applies on the existing Compose volume, not only on a fresh Testcontainers database.
