package gdg.sharinglog.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LegacySchemaFlywayConfiguration {

    private static final String FLYWAY_SCHEMA_HISTORY = "flyway_schema_history";

    private static final Map<String, Set<String>> LEGACY_V1_SCHEMA = Map.of(
            "users", Set.of("id", "provider", "provider_user_id", "email", "password", "nickname"),
            "sharing_groups", Set.of("id", "name", "created_by_user_id", "created_at"),
            "group_members", Set.of("id", "group_id", "user_id", "role", "joined_at"),
            "group_invitations", Set.of(
                    "id",
                    "group_id",
                    "created_by_user_id",
                    "code_hash",
                    "created_at",
                    "expires_at",
                    "revoked_at"
            )
    );

    @Bean
    FlywayConfigurationCustomizer legacySchemaBaselineCustomizer() {
        return configuration -> {
            try (Connection connection = configuration.getDataSource().getConnection()) {
                if (matchesLegacyV1Schema(connection)) {
                    configuration
                            .baselineOnMigrate(true)
                            .baselineVersion(MigrationVersion.fromVersion("1"))
                            .baselineDescription("Legacy Hibernate schema");
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to inspect the database before Flyway migration", exception);
            }
        };
    }

    static boolean matchesLegacyV1Schema(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        Map<String, String> tableNames = tableNames(metadata, catalog, schema);

        if (tableNames.containsKey(FLYWAY_SCHEMA_HISTORY)
                || !tableNames.keySet().equals(LEGACY_V1_SCHEMA.keySet())) {
            return false;
        }

        for (Map.Entry<String, Set<String>> expectedTable : LEGACY_V1_SCHEMA.entrySet()) {
            String actualTableName = tableNames.get(expectedTable.getKey());
            Set<String> actualColumns = columnNames(metadata, catalog, schema, actualTableName);
            if (!actualColumns.equals(expectedTable.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> tableNames(
            DatabaseMetaData metadata,
            String catalog,
            String schema
    ) throws SQLException {
        Map<String, String> tableNames = new LinkedHashMap<>();
        try (ResultSet tables = metadata.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableNames.put(tableName.toLowerCase(Locale.ROOT), tableName);
            }
        }
        return tableNames;
    }

    private static Set<String> columnNames(
            DatabaseMetaData metadata,
            String catalog,
            String schema,
            String tableName
    ) throws SQLException {
        Set<String> columnNames = new LinkedHashSet<>();
        try (ResultSet columns = metadata.getColumns(catalog, schema, tableName, "%")) {
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columnNames;
    }
}
