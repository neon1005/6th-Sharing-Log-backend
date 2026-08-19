# Flyway schema policy

The default application configuration never baselines an existing schema
automatically. New databases run all migrations from `V1`.

For a database that already contains exactly the four pre-rotation core tables,
`LegacySchemaFlywayConfiguration` verifies the table and column names and then
baselines that schema at version `1`. Flyway applies migrations `V2` and later.
An empty database still runs all migrations from `V1`.

An unknown non-empty schema, an extra table, or an unexpected column is not
baselined automatically. It keeps failing closed so the database can be
inspected before any migration is applied.

Do not set `baseline-on-migrate=true` in a long-lived application profile.
The guarded customizer enables it only for the recognized legacy schema and
leaves the default disabled for every other database.

Vendor-specific scripts live below `h2` and `mysql`. Spring Boot substitutes
the `{vendor}` segment in `spring.flyway.locations`, so only one vendor's
version stream is loaded for a datasource.
