package com.chatbot.saas.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V19 deletes data, so both of its branches are checked on a database migrated to V18 first:
 * the untouched seed business is removed, but one that was put to use is kept.
 */
class LegacyFlowCleanupMigrationTest {

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void start() throws Exception {
        postgres = EmbeddedPostgres.start();
    }

    @AfterAll
    static void stop() throws Exception {
        postgres.close();
    }

    private DataSource freshDatabase(String name) throws Exception {
        try (Connection c = postgres.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        return postgres.getDatabase("postgres", name);
    }

    private Flyway flyway(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private long count(DataSource dataSource, String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    private void exec(DataSource dataSource, String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    @Test
    void unusedSeedBusinessAndLegacyTablesAreRemoved() throws Exception {
        DataSource db = freshDatabase("cleanup_unused");
        flyway(db, "18").migrate();
        assertEquals(1, count(db, "SELECT count(*) FROM chatbot_flows"), "V8 seeded a flow");

        flyway(db, "19").migrate();

        assertEquals(0, count(db, "SELECT count(*) FROM businesses WHERE email = 'sample@example.com'"));
        assertEquals(0, count(db, "SELECT count(*) FROM information_schema.tables WHERE table_name IN "
                + "('chatbot_flows', 'flow_steps', 'conversation_data')"));
        assertEquals(0, count(db, "SELECT count(*) FROM information_schema.columns WHERE table_name = 'conversations' "
                + "AND column_name IN ('flow_id', 'current_step_id')"));
    }

    @Test
    void seedBusinessThatWasPutToUseIsKept() throws Exception {
        DataSource db = freshDatabase("cleanup_used");
        flyway(db, "18").migrate();
        // Someone adopted the demo business: gave it a category and a legacy conversation
        exec(db, "INSERT INTO categories (business_id, name, sort_order, is_active, created_at) "
                + "SELECT id, 'Shoes', 0, true, now() FROM businesses WHERE email = 'sample@example.com'");
        exec(db, "INSERT INTO customers (business_id, instagram_user_id, first_interaction_at, last_interaction_at) "
                + "SELECT id, 'ig-1', now(), now() FROM businesses WHERE email = 'sample@example.com'");
        exec(db, "INSERT INTO conversations (customer_id, business_id, flow_id, status, started_at, updated_at) "
                + "SELECT c.id, c.business_id, 1, 'ACTIVE', now(), now() FROM customers c");

        flyway(db, "19").migrate();

        assertEquals(1, count(db, "SELECT count(*) FROM businesses WHERE email = 'sample@example.com'"));
        assertEquals(1, count(db, "SELECT count(*) FROM conversations"), "conversations survive the column drop");
    }

    @Test
    void directusGrantsApplyCleanlyAfterCleanup() throws Exception {
        DataSource db = freshDatabase("cleanup_grants");
        exec(db, "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'directus') "
                + "THEN CREATE ROLE directus; END IF; END $$");

        Flyway.configure().dataSource(db).locations("classpath:db/migration", "classpath:db/postgres").load().migrate();

        assertEquals(1, count(db, "SELECT count(*) FROM information_schema.role_table_grants "
                + "WHERE grantee = 'directus' AND table_name = 'products' AND privilege_type = 'INSERT'"));
        assertEquals(0, count(db, "SELECT count(*) FROM information_schema.role_table_grants "
                + "WHERE grantee = 'directus' AND table_name = 'businesses'"));
    }
}
