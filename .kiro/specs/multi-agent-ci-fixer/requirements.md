# Requirements Document

## Introduction

The Multi-Agent CI Fixer is an automated system specifically designed for Java Spring projects that triages Jenkins build failures, analyzes Maven/Gradle build logs, generates Spring-aware code fixes using external OpenAI-compatible APIs (OpenRouter, OpenAI, etc.), validates the fixes, and creates GitHub pull requests with notifications to stakeholders. The system is designed to be self-hosted with minimal dependencies, using Java 8 compatibility and Docker for deployment.

## Requirements

### Requirement 1

**User Story:** As a development team, I want an automated system to receive Jenkins build failure notifications, so that failed builds can be immediately triaged without manual intervention.

#### Acceptance Criteria

1. WHEN a Jenkins build fails THEN the system SHALL receive a webhook payload containing job name, build number, branch, repository URL, commit SHA, and build logs
2. WHEN the webhook is received THEN the system SHALL persist the build information to the database within 5 seconds
3. WHEN the build information is persisted THEN the system SHALL create a planning task and add it to the processing queue
4. IF the webhook payload is malformed THEN the system SHALL return an error response and log the invalid payload

### Requirement 2

**User Story:** As a developer, I want the system to automatically analyze build failure logs and create a structured plan for fixing the issues, so that I can understand what went wrong and how it might be resolved.

#### Acceptance Criteria

1. WHEN a planning task is processed THEN the system SHALL parse Maven/Gradle build logs to identify Spring-specific error types (compilation errors, test failures, dependency conflicts, Spring context issues, annotation problems)
2. WHEN Spring-specific errors are identified THEN the system SHALL create a step-by-step plan with ordered fix actions targeting Spring components, configurations, and dependencies
3. WHEN the plan is created THEN the system SHALL store the plan in the database with references to specific Java classes, Spring configurations, and Maven/Gradle modules
4. IF no recognizable Spring/Java errors are found THEN the system SHALL create a plan indicating manual investigation is needed

### Requirement 3

**User Story:** As a developer, I want the system to automatically identify and retrieve relevant source files that likely contain the root cause of build failures, so that fixes can be targeted to the right locations.

#### Acceptance Criteria

1. WHEN a retrieval task is processed THEN the system SHALL clone or update the Spring project repository to a working directory
2. WHEN the repository is available THEN the system SHALL analyze Java stack traces, Maven/Gradle compiler errors, and Spring context failures to identify candidate Java source files, configuration files, and pom.xml/build.gradle
3. WHEN candidate files are identified THEN the system SHALL rank them by relevance (Java stack trace hits > Spring configuration errors > Maven/Gradle dependency issues > lexical matches in Java files)
4. WHEN files are ranked THEN the system SHALL store the candidate file list with reasoning for each file, prioritizing Spring components (@Controller, @Service, @Repository, @Configuration)

### Requirement 4

**User Story:** As a developer, I want the system to generate minimal code patches using an LLM to fix identified issues, so that build failures can be resolved automatically without over-engineering.

#### Acceptance Criteria

1. WHEN a patch task is processed THEN the system SHALL send Java source file content, Spring context information, and error context to external OpenAI-compatible API with Spring-specific prompting
2. WHEN the external API responds THEN the system SHALL validate that the response contains a proper unified diff format for Java files and follows Spring best practices
3. WHEN a valid diff is received THEN the system SHALL apply the patch using JGit to the working Spring project repository
4. IF the patch fails to apply THEN the system SHALL retry with updated Spring context and dependency information up to 3 times per file
5. WHEN patches are applied THEN the system SHALL commit changes with descriptive commit messages referencing Spring components and Maven/Gradle modules

### Requirement 5

**User Story:** As a developer, I want the system to validate that generated fixes actually resolve the build issues, so that only working solutions are proposed.

#### Acceptance Criteria

1. WHEN patches are applied THEN the system SHALL run Maven/Gradle compilation and Spring Boot tests in the working directory
2. WHEN Maven/Gradle validation passes and Spring context loads successfully THEN the system SHALL mark the fix as successful and proceed to PR creation
3. WHEN validation fails THEN the system SHALL extract Maven/Gradle error information and Spring context failures to retry the fix process up to 3 times
4. IF all retry attempts fail THEN the system SHALL mark the build as requiring manual intervention with Spring-specific error details

### Requirement 6

**User Story:** As a developer, I want the system to create GitHub pull requests with the generated fixes, so that I can review and merge the automated solutions.

#### Acceptance Criteria

1. WHEN validation succeeds THEN the system SHALL create a new branch with naming pattern "ci-fix/{buildId}"
2. WHEN the branch is created THEN the system SHALL push commits to the remote repository
3. WHEN commits are pushed THEN the system SHALL create a GitHub PR with title "Fix: Jenkins build #{buildNumber} ({shortSha})"
4. WHEN the PR is created THEN the system SHALL include plan summary, file diffs, validation logs, and checklist in the PR description
5. WHEN the PR is created THEN the system SHALL apply appropriate labels including "ci-fix" and "automated"

### Requirement 7

**User Story:** As a stakeholder, I want to receive notifications when the system processes build failures, so that I'm aware of automated fix attempts and their outcomes.

#### Acceptance Criteria

1. WHEN a PR is created THEN the system SHALL send email notifications to commit authors and configured recipients
2. WHEN notifications are sent THEN the system SHALL include links to the PR, Jenkins build, and diff summary
3. WHEN the process completes THEN the system SHALL update the build status to indicate success or failure
4. IF the process fails THEN the system SHALL send notification indicating manual intervention is required

### Requirement 8

**User Story:** As a system administrator, I want the system to be deployable via Docker with minimal configuration, so that it can be easily set up and maintained.

#### Acceptance Criteria

1. WHEN the system is deployed THEN it SHALL run as a single Spring Boot application with Java 8 compatibility
2. WHEN deployed THEN the system SHALL use PostgreSQL for persistence and external OpenAI-compatible APIs for code generation
3. WHEN configured THEN the system SHALL support environment variable configuration for tokens and endpoints
4. WHEN running THEN the system SHALL expose health check endpoints and metrics for monitoring

### Requirement 9

**User Story:** As a security-conscious administrator, I want the system to handle secrets securely and validate inputs, so that the automated process doesn't introduce security vulnerabilities.

#### Acceptance Criteria

1. WHEN handling GitHub and Jenkins tokens THEN the system SHALL store only references in the database with actual values in environment variables
2. WHEN receiving webhook payloads THEN the system SHALL validate HMAC signatures using shared secrets
3. WHEN generating patches THEN the system SHALL limit file modifications and validate diff safety before application
4. WHEN creating PRs THEN the system SHALL redact sensitive information from logs and descriptions

### Requirement 10

**User Story:** As a developer, I want the system to handle various failure scenarios gracefully, so that edge cases don't cause the automation to break or behave unpredictably.

#### Acceptance Criteria

1. WHEN processing tasks THEN the system SHALL implement retry logic with exponential backoff for transient failures
2. WHEN repositories are large THEN the system SHALL limit log parsing to the last 300 lines and failing modules
3. WHEN external API responses are invalid THEN the system SHALL validate output format and request regeneration
4. WHEN multiple builds fail simultaneously THEN the system SHALL process them independently without interference
5. IF system resources are constrained THEN the system SHALL queue tasks and process them sequentially