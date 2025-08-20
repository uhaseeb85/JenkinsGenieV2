-- CI Fixer Database Initialization Script
-- This script sets up the initial database schema and configuration

-- Create database if it doesn't exist (handled by POSTGRES_DB env var)
-- The database is automatically created by the PostgreSQL Docker image

-- Create application user with limited privileges for production
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'cifixer_app') THEN
        CREATE ROLE cifixer_app WITH LOGIN PASSWORD 'cifixer_app_password';
    END IF;
END
$$;

-- Grant necessary permissions
GRANT CONNECT ON DATABASE cifixer TO cifixer_app;
GRANT USAGE ON SCHEMA public TO cifixer_app;
GRANT CREATE ON SCHEMA public TO cifixer_app;

-- Create extension for UUID generation if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create extension for JSON operations
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- Set timezone
SET timezone = 'UTC';

-- Log initialization
\echo 'Database initialization completed successfully'