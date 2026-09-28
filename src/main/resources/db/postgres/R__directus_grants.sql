-- Least-privilege access for the Directus catalog UI (see docker/postgres/init). Repeatable:
-- Flyway re-applies it whenever this file changes, so extend it here when Directus needs more.
-- Skipped silently where no "directus" role exists (e.g. local dev without Directus).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'directus') THEN
        -- Shop owners add and edit their catalog. No DELETE: products are retired by
        -- switching is_active off, and orders reference them.
        GRANT SELECT, INSERT, UPDATE ON categories, products TO directus;
        GRANT USAGE, SELECT ON SEQUENCE categories_id_seq, products_id_seq TO directus;

        -- Nothing on businesses (encrypted Meta tokens, API key hashes) or on customers,
        -- orders and messages (customer personal data). Shop owners are scoped by the
        -- business_id custom field on their Directus user, so they never need these.
        REVOKE ALL ON businesses, customers, orders, messages, conversations, conversation_data
            FROM directus;
    END IF;
END
$$;
