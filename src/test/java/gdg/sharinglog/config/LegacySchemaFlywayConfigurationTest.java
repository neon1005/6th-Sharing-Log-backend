package gdg.sharinglog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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

            assertThat(migrationResult.migrationsExecuted).isEqualTo(14);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("15");
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
