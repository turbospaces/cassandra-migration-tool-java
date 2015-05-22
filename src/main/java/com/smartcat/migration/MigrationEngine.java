package com.smartcat.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Session;

public class MigrationEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationEngine.class);

    private MigrationEngine(){
    }

    /**
     * Create migrator out of session fully prepared for doing migration of resources
     *
     * @param session Datastax driver session object
     * @return migrator instance with versioner and session which can migrate resources
     */
    public static Migrator withSession(final Session session) {
        return new Migrator(session);
    }

    public static class Migrator{
        private final Session session;
        private final CassandraVersioner versioner;

        public Migrator(final Session session) {
            this.session = session;
            this.versioner = new CassandraVersioner(session);
        }

        /**
         * Method that executes all migration from migration resources that are higher version than db version. If migration
         * fails, method will exit.
         *
         * @return Success of migration
         */
        public boolean migrate(final MigrationResources resources){
            LOGGER.debug("Start migration");

            for (final Migration migration : resources.getMigrations()) {
                final MigrationType type = migration.getType();
                final int migrationVersion = migration.getVersion();
                final int version = versioner.getCurrentVersion(type);

                LOGGER.info("Db is version {} for type {}.", version, type.name());
                LOGGER.info("Compare {} migration version {} with description {}", type.name(), migrationVersion,
                        migration.getDescription());

                // If version is same or older dont execute
                if (migrationVersion <= version) {
                    continue;
                }

                // Inject session
                migration.setSession(session);

                final long start = System.currentTimeMillis();
                LOGGER.info("Start executing migration to version {}.", migrationVersion);

                try {
                    migration.execute();
                } catch (final MigrationException e) {
                    LOGGER.error("Failed to execute migration version {}!", migrationVersion);
                    LOGGER.error(e.getMessage(), e);
                    return false;
                }

                final long end = System.currentTimeMillis();
                final long seconds = (end - start) / 1000;
                LOGGER.info("Migration to version {} finished in {} seconds.", migrationVersion, seconds);
                LOGGER.info("Migration description: {}", migration.getDescription());

                // After successful migration update db schema version
                if (!versioner.updateVersion(migration)) {
                    LOGGER.error("Db schema update failed for migration version {}!", migrationVersion);
                    return false;
                }
            }

            return true;
        }
    }

}
