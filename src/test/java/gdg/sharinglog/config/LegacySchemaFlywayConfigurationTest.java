package gdg.sharinglog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class LegacySchemaFlywayConfigurationTest {

    @Test
    void recognizesTheExactLegacySchema() throws SQLException {
        try (Connection connection = connection()) {
            createLegacySchema(connection);

            assertThat(LegacySchemaFlywayConfiguration.matchesLegacyV1Schema(connection)).isTrue();
        }
    }

    @Test
    void baselinesTheLegacySchemaAndAppliesLaterMigrations() throws SQLException {
        try (Connection connection = connection()) {
            createLegacySchema(connection);
            String url = connection.getMetaData().getURL();
            var flywayConfiguration = Flyway.configure()
                    .dataSource(url, "sa", "")
                    .locations("classpath:db/migration/h2");

            new LegacySchemaFlywayConfiguration()
                    .legacySchemaBaselineCustomizer()
                    .customize(flywayConfiguration);

            Flyway flyway = flywayConfiguration.load();
            var migrationResult = flyway.migrate();

            assertThat(migrationResult.migrationsExecuted).isEqualTo(15);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");
        }
    }

    @Test
    void convertsExistingBiweeklyAnchorToTheEquivalentDueDate() throws SQLException {
        try (Connection connection = connection()) {
            String url = connection.getMetaData().getURL();
            Flyway.configure()
                    .dataSource(url, "sa", "")
                    .locations("classpath:db/migration/h2")
                    .target("15")
                    .load()
                    .migrate();
            execute(connection, """
                    INSERT INTO users (provider, provider_user_id)
                    VALUES ('GOOGLE', 'biweekly-migration-user')
                    """);
            execute(connection, """
                    INSERT INTO sharing_groups (
                        name, created_by_user_id, created_at,
                        public_id, time_zone_id, week_starts_on
                    ) VALUES (
                        '우리 집', 1, TIMESTAMP WITH TIME ZONE '2026-08-01 00:00:00+00:00',
                        '11111111-1111-4111-8111-111111111111', 'Asia/Seoul', 'MONDAY'
                    )
                    """);
            execute(connection, """
                    INSERT INTO group_members (
                        group_id, user_id, role, joined_at,
                        public_id, status, version, activation_generation
                    ) VALUES (
                        1, 1, 'OWNER', TIMESTAMP WITH TIME ZONE '2026-08-01 00:00:00+00:00',
                        '22222222-2222-4222-8222-222222222222', 'ACTIVE', 0, 1
                    )
                    """);
            execute(connection, """
                    INSERT INTO chores (
                        public_id, group_id, created_by_membership_id, name,
                        frequency, eligibility_mode, due_time, weekly_due_day,
                        biweekly_anchor_date, active, created_at, version,
                        eligibility_revision, schedule_revision
                    ) VALUES (
                        '33333333-3333-4333-8333-333333333333', 1, 1, '분리수거',
                        'BIWEEKLY', 'ALL_ACTIVE_MEMBERS', TIME '20:00:00', NULL,
                        DATE '2026-08-17', TRUE,
                        TIMESTAMP WITH TIME ZONE '2026-08-01 00:00:00+00:00', 0, 0, 0
                    )
                    """);

            Flyway flyway = Flyway.configure()
                    .dataSource(url, "sa", "")
                    .locations("classpath:db/migration/h2")
                    .load();
            var migrationResult = flyway.migrate();

            assertThat(migrationResult.migrationsExecuted).isEqualTo(1);
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT biweekly_due_date
                         FROM chores
                         WHERE public_id = '33333333-3333-4333-8333-333333333333'
                         """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1, LocalDate.class))
                        .isEqualTo(LocalDate.of(2026, 8, 30));
            }
        }
    }

    @Test
    void rejectsAnEmptySchema() throws SQLException {
        try (Connection connection = connection()) {
            assertThat(LegacySchemaFlywayConfiguration.matchesLegacyV1Schema(connection)).isFalse();
        }
    }

    @Test
    void rejectsASchemaAlreadyManagedByFlyway() throws SQLException {
        try (Connection connection = connection()) {
            createLegacySchema(connection);
            execute(connection, """
                    CREATE TABLE flyway_schema_history (
                        installed_rank INT NOT NULL PRIMARY KEY
                    )
                    """);

            assertThat(LegacySchemaFlywayConfiguration.matchesLegacyV1Schema(connection)).isFalse();
        }
    }

    @Test
    void rejectsAnUnknownNonEmptySchema() throws SQLException {
        try (Connection connection = connection()) {
            createLegacySchema(connection);
            execute(connection, "CREATE TABLE unrelated_table (id BIGINT PRIMARY KEY)");

            assertThat(LegacySchemaFlywayConfiguration.matchesLegacyV1Schema(connection)).isFalse();
        }
    }

    @Test
    void rejectsALegacyTableWithUnexpectedColumns() throws SQLException {
        try (Connection connection = connection()) {
            createLegacySchema(connection);
            execute(connection, "ALTER TABLE users ADD unexpected_column VARCHAR(20)");

            assertThat(LegacySchemaFlywayConfiguration.matchesLegacyV1Schema(connection)).isFalse();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
    }

    private static void createLegacySchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    provider VARCHAR(20) NOT NULL,
                    provider_user_id VARCHAR(255) NOT NULL,
                    email VARCHAR(255),
                    password VARCHAR(255),
                    nickname VARCHAR(255)
                )
                """);
        execute(connection, """
                CREATE TABLE sharing_groups (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) NOT NULL,
                    created_by_user_id BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        execute(connection, """
                CREATE TABLE group_members (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    group_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    joined_at TIMESTAMP NOT NULL
                )
                """);
        execute(connection, """
                CREATE TABLE group_invitations (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    group_id BIGINT NOT NULL,
                    created_by_user_id BIGINT NOT NULL,
                    code_hash VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    revoked_at TIMESTAMP
                )
                """);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
