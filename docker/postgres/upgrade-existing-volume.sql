-- One-time upgrade for a database volume created before the least-privilege roles existed
-- (when the app and Directus both connected as "postgres"). New volumes don't need this: the
-- init script in docker/postgres/init creates the roles.
--
-- Usage (replace the passwords with APP_DB_PASSWORD / DIRECTUS_DB_PASSWORD from .env):
--   docker compose exec -T postgres psql -U postgres -d chatbot_saas -v ON_ERROR_STOP=1 \
--     -v app_password='...' -v directus_password='...' < docker/postgres/upgrade-existing-volume.sql
-- then: docker compose up -d   (the app re-applies the Directus grants on startup)

CREATE ROLE chatbot_app LOGIN PASSWORD :'app_password';
CREATE ROLE directus LOGIN PASSWORD :'directus_password';

ALTER DATABASE chatbot_saas OWNER TO chatbot_app;
ALTER SCHEMA public OWNER TO chatbot_app;
GRANT CONNECT ON DATABASE chatbot_saas TO directus;
GRANT USAGE, CREATE ON SCHEMA public TO directus;

-- Hand existing tables to their new owners. Sequences behind serial columns follow their
-- table automatically. (REASSIGN OWNED BY postgres can't be used: it would also try to move
-- system objects owned by the bootstrap superuser.)
DO $$
DECLARE
    t record;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        IF t.tablename LIKE 'directus\_%' THEN
            EXECUTE format('ALTER TABLE public.%I OWNER TO directus', t.tablename);
        ELSE
            EXECUTE format('ALTER TABLE public.%I OWNER TO chatbot_app', t.tablename);
        END IF;
    END LOOP;
END
$$;
