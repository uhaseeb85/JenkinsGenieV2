-- Additional database indexes for performance optimization
-- These indexes will be created after Flyway migrations run

-- Note: The main schema is created by Flyway migrations in the application
-- This script creates additional performance indexes that are environment-specific

-- Function to create index if it doesn't exist
CREATE OR REPLACE FUNCTION create_index_if_not_exists(index_name text, table_name text, index_definition text)
RETURNS void AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = index_name) THEN
        EXECUTE format('CREATE INDEX %I ON %I %s', index_name, table_name, index_definition);
        RAISE NOTICE 'Created index: %', index_name;
    ELSE
        RAISE NOTICE 'Index already exists: %', index_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Additional performance indexes (will be created after application starts)
-- These are supplementary to the main indexes created by Flyway

-- Composite index for task queue processing with status and created_at
SELECT create_index_if_not_exists(
    'idx_tasks_queue_processing',
    'tasks',
    '(status, created_at) WHERE status IN (''PENDING'', ''PROCESSING'')'
);

-- Index for build status monitoring
SELECT create_index_if_not_exists(
    'idx_builds_status_created',
    'builds',
    '(status, created_at DESC)'
);

-- Index for cleanup operations
SELECT create_index_if_not_exists(
    'idx_builds_cleanup',
    'builds',
    '(created_at) WHERE status IN (''COMPLETED'', ''FAILED'')'
);

-- Partial index for active pull requests
SELECT create_index_if_not_exists(
    'idx_pull_requests_active',
    'pull_requests',
    '(build_id, status) WHERE status = ''CREATED'''
);

-- GIN index for JSONB payload searches (if needed for debugging)
SELECT create_index_if_not_exists(
    'idx_tasks_payload_gin',
    'tasks',
    'USING GIN (payload)'
);

-- Drop the helper function
DROP FUNCTION create_index_if_not_exists(text, text, text);

\echo 'Additional indexes setup completed'