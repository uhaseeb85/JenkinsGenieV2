-- Multi-Agent CI Fixer Database Schema
-- Initial migration for PostgreSQL

-- Core builds table
CREATE TABLE builds (
    id BIGSERIAL PRIMARY KEY,
    job VARCHAR(255) NOT NULL,
    build_number INT NOT NULL,
    branch VARCHAR(255) NOT NULL,
    repo_url TEXT NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Plans table for storing structured fix plans
CREATE TABLE plans (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    plan_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Tasks table for the task queue system
CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    payload JSONB,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Candidate files identified for potential fixes
CREATE TABLE candidate_files (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    rank_score DECIMAL(5,2) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Patches generated and applied to fix issues
CREATE TABLE patches (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    unified_diff TEXT NOT NULL,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    apply_log TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Validation results from build/test execution
CREATE TABLE validations (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    validation_type VARCHAR(32) NOT NULL, -- COMPILE, TEST
    exit_code INT NOT NULL,
    stdout TEXT,
    stderr TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Pull requests created for fixes
CREATE TABLE pull_requests (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    pr_number INT,
    pr_url TEXT,
    branch_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Performance indexes for task queue operations
CREATE INDEX idx_tasks_status_type ON tasks(status, type);
CREATE INDEX idx_tasks_build_id ON tasks(build_id);
CREATE INDEX idx_builds_status ON builds(status);
CREATE INDEX idx_builds_created_at ON builds(created_at);
CREATE INDEX idx_candidate_files_build_rank ON candidate_files(build_id, rank_score DESC);
CREATE INDEX idx_patches_build_id ON patches(build_id);
CREATE INDEX idx_validations_build_id ON validations(build_id);
CREATE INDEX idx_pull_requests_build_id ON pull_requests(build_id);

-- Unique constraints
ALTER TABLE builds ADD CONSTRAINT uk_builds_job_build_number UNIQUE (job, build_number);
ALTER TABLE plans ADD CONSTRAINT uk_plans_build_id UNIQUE (build_id);
ALTER TABLE pull_requests ADD CONSTRAINT uk_pull_requests_build_id UNIQUE (build_id);