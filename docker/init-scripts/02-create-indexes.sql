-- Additional database indexes for performance optimization
-- These indexes will be created after Flyway migrations run

-- Note: The main schema is created by Flyway migrations in the application
-- This script creates additional performance indexes that are environment-specific

-- Since the Flyway migrations haven't run yet, we'll just log a message
-- The application will create these indexes when it starts up
\echo 'Database schema will be created by Flyway migrations when the application starts';
\echo 'Additional performance indexes will be created at that time';

\echo 'Database initialization completed - Flyway migrations will run when application starts'