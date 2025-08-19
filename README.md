# Multi-Agent CI Fixer

An automated system specifically designed for Java Spring projects that triages Jenkins build failures, analyzes Maven/Gradle build logs, generates Spring-aware code fixes using a local LLM, validates the fixes, and creates GitHub pull requests with notifications to stakeholders.

## Project Structure

```
src/main/java/com/example/cifixer/
├── MultiAgentCiFixerApplication.java  # Main Spring Boot application
├── core/                              # Core interfaces and classes
│   ├── Agent.java                     # Base agent interface
│   ├── Task.java                      # Task representation
│   ├── TaskResult.java                # Task processing result
│   ├── TaskStatus.java                # Task status enumeration
│   ├── TaskType.java                  # Task type enumeration
│   └── TaskQueue.java                 # Task queue interface
├── web/                               # REST controllers and webhook handlers
├── agents/                            # Agent implementations
├── git/                               # Git operations and repository management
├── llm/                               # LLM integration and prompt management
├── store/                             # Data persistence layer (JPA entities, repositories)
└── util/                              # Utility classes and helper functions
```

## Technology Stack

- **Java 8** - Base language with compatibility requirements
- **Spring Boot 2.7.x** - Application framework
- **Spring Data JPA** - Data persistence
- **PostgreSQL** - Primary database
- **JGit** - Git operations
- **OkHttp** - HTTP client for external APIs
- **Jackson** - JSON processing
- **Flyway** - Database migrations
- **Maven** - Build tool

## Key Dependencies

- Spring Boot Starter Web, Data JPA, Mail, Actuator
- PostgreSQL driver and Flyway for database
- JGit for Git operations
- OkHttp for HTTP client
- Jackson for JSON processing
- Hibernate Types for JSONB support
- Testcontainers for integration testing

## Configuration

The application uses `application.yml` for configuration with environment variable support:

- Database connection (PostgreSQL)
- GitHub API token and settings
- Jenkins webhook secret
- LLM endpoint configuration
- SMTP settings for notifications
- Working directory for Git operations

## Getting Started

1. **Prerequisites:**
   - Java 8 or higher
   - PostgreSQL database
   - Local LLM endpoint (Ollama/LM Studio)

2. **Build:**
   ```bash
   mvn clean compile
   ```

3. **Run Tests:**
   ```bash
   mvn test
   ```

4. **Run Application:**
   ```bash
   mvn spring-boot:run
   ```

## Core Concepts

### Agents
The system uses a multi-agent architecture where each agent handles a specific domain:
- **Planner Agent**: Analyzes build logs and creates fix plans
- **Repo Agent**: Handles Git operations
- **Retriever Agent**: Identifies candidate files for fixing
- **Code-Fix Agent**: Generates and applies patches using LLM
- **Validator Agent**: Validates fixes by running builds
- **PR Agent**: Creates GitHub pull requests
- **Notification Agent**: Sends email notifications

### Task Processing
Tasks flow through the system with proper status tracking and retry logic:
- Tasks are queued in the database
- Orchestrator dispatches tasks to appropriate agents
- Results are tracked with metadata for debugging
- Failed tasks are retried with exponential backoff

### Spring Project Awareness
The system is specifically designed for Spring Boot projects:
- Detects Spring Boot version and build tool (Maven/Gradle)
- Understands Spring annotations and context
- Parses Spring-specific error messages
- Generates Spring-aware code fixes

## Next Steps

This is the foundational structure. The next tasks will implement:
1. Database schema and JPA entities
2. Webhook API for Jenkins integration
3. Individual agent implementations
4. LLM integration for code generation
5. GitHub API integration for PR creation
6. Email notifications for stakeholders