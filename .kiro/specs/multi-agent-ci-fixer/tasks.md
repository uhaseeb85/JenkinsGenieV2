# Implementation Plan

- [x] 1. Set up Spring Boot project structure and core interfaces





  - Create Spring Boot 2.7.x project with Java 8 compatibility
  - Define base Agent interface and TaskResult classes
  - Set up Maven dependencies for JGit, OkHttp, Jackson, and Spring Data JPA
  - Create package structure: web, core, agents, git, llm, store, util
  - _Requirements: 8.1, 8.2_

- [x] 2. Implement database schema and JPA entities





  - Create Flyway migration scripts for PostgreSQL schema
  - Implement JPA entities: Build, Task, Plan, CandidateFile, Patch, Validation, PullRequest
  - Create Spring Data repositories with custom query methods
  - Add database indexes for task queue performance
  - _Requirements: 8.2, 1.2, 1.3_

- [x] 3. Create webhook API and basic task queue





  - Implement WebhookController with Jenkins payload handling
  - Add HMAC signature validation for webhook security, this is optional , we should be able to skip signature validation also if needed.
  - Create TaskQueue service with database-backed queue management
  - Implement basic Orchestrator with scheduled task processing
  - Add BuildController for status monitoring
  - _Requirements: 1.1, 1.2, 1.3, 9.2_

- [x] 4. Implement Spring project analysis and context building





  - Create SpringProjectAnalyzer to detect Spring Boot version and build tool
  - Implement Maven/Gradle module detection and dependency parsing
  - Add Spring annotation discovery in Java source files
  - Create SpringProjectContext data structure
  - Write unit tests for project analysis functionality
  - _Requirements: 3.1, 3.2, 2.1_

- [x] 5. Build Planner Agent for Spring-specific log parsing





  - Implement PlannerAgent with Spring-aware log parsing
  - Add regex patterns for Maven compiler errors, Gradle build failures
  - Create Spring context failure parsing (NoSuchBeanDefinitionException, etc.)
  - Implement Java stack trace parsing with line number extraction
  - Generate structured plans with Spring-specific fix recommendations
  - Write comprehensive unit tests for log parsing scenarios
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 6. Create Repo Agent for Git operations and Spring project handling





  - Implement RepoAgent using JGit for clone/pull operations
  - Add working directory management per build ID
  - Create branch creation with "ci-fix/{buildId}" naming pattern
  - Implement commit and push operations with authentication
  - Add Maven/Gradle project structure validation
  - Write integration tests with test Git repositories
  - _Requirements: 3.1, 6.2, 6.3_

- [x] 7. Implement Retriever Agent for Spring-aware file ranking











  - Create RetrieverAgent with Spring component prioritization
  - Implement file ranking algorithm (stack traces > Spring errors > lexical matches)
  - Add special handling for Spring annotations (@Controller, @Service, @Repository, @Configuration)
  - Create candidate file scoring with reasoning
  - Build context window preparation for LLM consumption
  - Write unit tests for file ranking scenarios
  - _Requirements: 3.2, 3.3, 3.4_

- [x] 8. Build external API integration with Spring-aware prompting








  - Implement OpenAiCompatibleClient with HTTP calls to external API endpoints
  - Support multiple providers: OpenRouter, OpenAI, Anthropic, etc.
  - Create Spring-specific prompt templates with context injection
  - Add unified diff validation and safety checks
  - Implement token limit handling, rate limiting, and response parsing
  - Add retry logic for API failures and malformed responses
  - Write unit tests with mocked API responses
  - _Requirements: 4.1, 4.2, 9.3_

- [x] 9. Create Code-Fix Agent with Spring context integration







  - Implement CodeFixAgent combining SpringProjectContext with external API calls
  - Add patch application using JGit with conflict resolution
  - Create Spring-aware diff validation (Java syntax, annotation usage)
  - Implement retry logic with enhanced context on failures
  - Add commit message generation with Spring component references
  - Write integration tests with real patch application scenarios
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 10. Implement Validator Agent for Maven/Gradle builds





  - Create ValidatorAgent with Maven and Gradle command execution
  - Add Spring Boot test execution with proper classpath handling
  - Implement compilation validation with error extraction
  - Create validation result parsing and storage
  - Add retry mechanism with enhanced error context for fix iterations
  - Write integration tests with sample Spring Boot projects
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 11. Build PR Agent for GitHub integration





  - Implement PrAgent with GitHub REST API integration using OkHttp
  - Create branch pushing with authentication handling
  - Add PR creation with Spring-specific templates (title, description, labels)
  - Implement PR description generation with plan summary and diff details
  - Add error handling for GitHub API rate limits and failures
  - Write integration tests with GitHub API mocking
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 12. Create Notification Agent for stakeholder communication







  - Implement NotificationAgent with Spring Mail integration
  - Create HTML email templates with PR links and build status
  - Add recipient resolution (commit authors, configured lists)
  - Implement notification for both success and failure scenarios
  - Add email content generation with Spring project context
  - Write unit tests for email template generation
  - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [x] 13. Implement comprehensive error handling and retry logic







  - Add RetryHandler with exponential backoff for task failures
  - Implement safety guardrails for patch validation
  - Create input validation for webhook payloads and file paths
  - Add comprehensive logging with task and build ID correlation
  - Implement cleanup jobs for old working directories
  - Write unit tests for error scenarios and retry mechanisms
  - _Requirements: 9.1, 9.3, 9.4, 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 14. Add configuration management and security features





  - Implement application.yml configuration with environment variable support
  - Add SecretManager for token handling with redaction
  - Create webhook signature validation with HMAC-SHA256
  - Add SSL certificate validation for external API calls
  - Write security-focused unit tests
  - _Requirements: 8.3, 9.1, 9.2, 9.3, 9.4_

- [x] 15. Create comprehensive test suite





  - Write unit tests for all agent implementations with 80%+ coverage
  - Create integration tests using Testcontainers for PostgreSQL
  - Build end-to-end tests with sample Spring Boot projects
  - Add performance tests for task queue processing
  - Create test scenarios for various Spring error types
  - Implement test data factories and fixtures
  - _Requirements: All requirements validation_

- [x] 16. Build Docker deployment configuration





  - Create Dockerfile for Spring Boot application with Java 8 base image
  - Implement docker-compose.yml with PostgreSQL and MailHog services
  - Add environment variable configuration for external API integration
  - Create database initialization scripts and health checks
  - Add volume mounts for working directories
  - Write deployment documentation and troubleshooting guide
  - _Requirements: 8.1, 8.2, 8.4_

- [x] 17. Update Docker configuration for external API integration



  - Remove Ollama service from docker-compose.yml
  - Update environment variables to support external API configuration
  - Add API key management and multiple provider support
  - Update deployment scripts to remove LLM model setup
  - Modify health checks to validate external API connectivity
  - Update documentation to reflect external API architecture
  - _Requirements: 8.1, 8.2, 8.4_

- [x] 18. Implement monitoring and operational features





  - Add custom metrics for task processing latency and success rates using Micrometer
  - Implement structured JSON logging with correlation IDs for better observability
  - Add database connection monitoring and connection pooling configuration
  - Create admin endpoints for manual task retry and system status monitoring
  - Write operational runbooks and monitoring setup guide
  - _Requirements: 8.4, 10.1, 10.2, 10.3_

- [x] 19. Create deployment documentation and final validation





  - Write comprehensive deployment guide with Docker setup instructions
  - Create configuration examples for different environments (dev, staging, prod)
  - Document external API integration setup with OpenRouter, OpenAI, Anthropic
  - Create troubleshooting guide for common deployment issues
  - Write user guide for webhook configuration and GitHub integration
  - Document security best practices and API key management
  - _Requirements: All requirements final validation_