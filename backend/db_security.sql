-- =============================================================================
-- SOUIGAT — DB Security: Audit Log Protection
-- =============================================================================
-- Run as PostgreSQL superuser ONCE per environment:
--   docker compose exec db psql -U postgres -d souigat_db -f /app/db_security.sql
--
-- Purpose: Make audit_log append-only by revoking DELETE and UPDATE from the
--          application user. The Django app can only INSERT and SELECT.
-- =============================================================================

DO $$
BEGIN
    REVOKE DELETE, UPDATE ON audit_log FROM souigat_user;
    GRANT INSERT, SELECT ON audit_log TO souigat_user;
    GRANT USAGE ON SEQUENCE audit_log_id_seq TO souigat_user;
    RAISE NOTICE 'audit_log privileges + sequence configured successfully';
END $$;

-- Verify (should show: INSERT, SELECT only)
-- \dp audit_log
