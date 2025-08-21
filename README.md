# JenkinsGenieV2 - Multi-Agent CI Fixer

> **AI Context**: This is a production-ready multi-agent system that automatically fixes Jenkins build failures for Java Spring Boot projects using LLM-powered code generation. The system receives webhook notifications from Jenkins, analyzes build failures, generates targeted fixes using external LLM APIs, validates the fixes, and creates GitHub pull requests.

## 🎯 What This Project Does

**JenkinsGenieV2** is an intelligent CI/CD automation system that:
1. **Receives Jenkins webhook notifications** when builds fail
2. **Analyzes build logs** to understand Spring Boot specific errors (compilation, dependency, configuration issues)
3. **Ranks candidate files** for fixing using intelligent algorithms 
4. **Generates targeted code fixes** using external LLM APIs (OpenRouter, OpenAI, Anthropic)
5. **Validates fixes** by running Maven/Gradle builds and tests
6. **Creates GitHub pull requests** with comprehensive descriptions and validation results
7. **Sends notifications** to stakeholders via email

## 🏗️ Architecture Overview

### Multi-Agent System
The project uses a **multi-agent architecture** where specialized agents handle different aspects of the CI fixing process:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Jenkins       │───▶│  Webhook        │───▶│  Task Queue     │
│   Failure       │    │  Controller      │    │  (Database)     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                                         │
                       ┌─────────────────────────────────▼─────────────────--┐
                       │                Orchestrator                         │
                       └─────────────────────┬───────────────────────────────┘
                                             │
        ┌───────────────┬────────────────────┼────────────────────┬──────────────────┐
        │               │                    │                    │                  │
   ┌────▼────┐    ┌─────▼─────┐       ┌─────▼─────┐       ┌─────▼─────┐      ┌─────▼─────┐
   │ Planner │    │ Retriever │       │ Code-Fix  │       │ Validator │      │ PR Agent  │
   │ Agent   │    │ Agent     │       │ Agent     │       │ Agent     │      │           │
   └─────────┘    └───────────┘       └───────────┘       └───────────┘      └───────────┘
```

### Key Agents
- **🧠 Planner Agent**: Analyzes build logs and creates fix plans with Spring Boot context
- **📁 Retriever Agent**: Identifies candidate files for fixing using stack traces and error analysis  
- **⚙️ Repo Agent**: Handles Git operations (clone, branch, commit, push)
- **🤖 Code-Fix Agent**: Generates code patches using LLM APIs with Spring-specific prompts
- **✅ Validator Agent**: Validates fixes by running Maven/Gradle builds and tests
- **📋 PR Agent**: Creates GitHub pull requests with comprehensive descriptions
- **📧 Notification Agent**: Sends email notifications to stakeholders

## 🛠️ Technology Stack

### Core Technologies
- **Java 8** - Base language (legacy compatibility requirement)
- **Spring Boot 2.7.18** - Application framework with auto-configuration
- **Spring Data JPA** - Data persistence with PostgreSQL
- **PostgreSQL 14** - Primary database with JSONB support
- **Maven** - Build tool and dependency management

### External Integrations  
- **JGit** - Git operations (clone, branch, commit, push)
- **OkHttp 4.12** - HTTP client for GitHub and LLM APIs
- **Jackson** - JSON processing for API communication
- **Flyway** - Database schema migrations

### LLM Integration
- **OpenRouter** - Primary LLM provider (supports Claude, GPT-4, etc.)
- **OpenAI API** - Direct OpenAI integration support
- **Anthropic Claude** - Recommended model: `anthropic/claude-3.5-sonnet`
- **Custom Endpoints** - Support for local LLM servers (Ollama, LM Studio)

### Testing & Quality
- **JUnit 5** - Unit testing framework
- **Testcontainers** - Integration testing with PostgreSQL
- **MockWebServer** - HTTP API testing
- **JaCoCo** - Code coverage (80% instruction, 75% branch coverage required)
- **Awaitility** - Async testing support

## 🚀 Quick Start

### 1. Prerequisites
```bash
# Required
- Java 8+ 
- Docker & Docker Compose
- GitHub Personal Access Token (with repo permissions)
- LLM API access (OpenRouter recommended)

# Optional
- SMTP server for notifications
- Jenkins with webhook capability
```

### 2. Environment Setup
```bash
# Clone repository
git clone https://github.com/uhaseeb85/JenkinsGenieV2.git
cd JenkinsGenieV2

# Copy environment template
cp .env.example .env

# Edit .env with your configuration
vim .env
```

### 3. Essential Environment Variables
```bash
# LLM Configuration (Required)
LLM_API_KEY=your_openrouter_or_openai_key
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_MODEL=anthropic/claude-3.5-sonnet

# GitHub Integration (Required)
GITHUB_TOKEN=your_github_personal_access_token

# Database (Auto-configured in Docker)
POSTGRES_PASSWORD=secure_password_here

# Jenkins Webhook Security (Recommended)
JENKINS_WEBHOOK_SECRET=your_webhook_secret
```

### 4. Deploy with Docker
```bash
# Start all services
docker-compose up -d

# Check application health
curl http://localhost:8081/actuator/health

# View logs
docker-compose logs -f ci-fixer
```

### 5. Configure Jenkins Webhook
Add webhook to your Jenkins job:
```groovy
// In Jenkins Pipeline or Build Configuration
post {
    failure {
        script {
            def payload = [
                job: env.JOB_NAME,
                buildNumber: env.BUILD_NUMBER,
                branch: env.BRANCH_NAME,
                repoUrl: scm.userRemoteConfigs[0].url,
                commitSha: env.GIT_COMMIT,
                buildLogs: currentBuild.rawBuild.getLog(1000).join('\n')
            ]
            
            httpRequest(
                httpMode: 'POST',
                url: 'http://your-ci-fixer-host:8081/api/webhook/jenkins',
                contentType: 'APPLICATION_JSON',
                requestBody: groovy.json.JsonOutput.toJson(payload)
            )
        }
    }
}
```

## 📁 Project Structure

```
JenkinsGenieV2/
├── src/main/java/com/example/cifixer/
│   ├── MultiAgentCiFixerApplication.java      # 🚀 Main Spring Boot application
│   ├── agents/                                # 🤖 Agent implementations
│   │   ├── PlannerAgent.java                  # Analyzes build failures
│   │   ├── RetrieverAgent.java                # Finds candidate files  
│   │   ├── RepoAgent.java                     # Git operations
│   │   ├── CodeFixAgent.java                  # LLM-powered code generation
│   │   ├── ValidatorAgent.java                # Build validation
│   │   ├── PrAgent.java                       # GitHub PR creation
│   │   └── NotificationAgent.java             # Email notifications
│   ├── core/                                  # 🏗️ Core framework
│   │   ├── Agent.java                         # Base agent interface
│   │   ├── Task.java                          # Task entity
│   │   ├── TaskQueue.java                     # Task queue interface
│   │   ├── TaskResult.java                    # Task processing result
│   │   ├── TaskStatus.java                    # Task status enum
│   │   ├── TaskType.java                      # Task type enum
│   │   └── Orchestrator.java                  # Task orchestration
│   ├── web/                                   # 🌐 REST API & Webhooks
│   │   ├── WebhookController.java             # Jenkins webhook handler
│   │   └── WebhookValidator.java              # Webhook security
│   ├── store/                                 # 💾 Data persistence (JPA)
│   │   ├── Build.java                         # Build entity
│   │   ├── Task.java                          # Task entity  
│   │   ├── PullRequest.java                   # PR tracking
│   │   └── *Repository.java                   # JPA repositories
│   ├── llm/                                   # 🧠 LLM integration
│   │   ├── LlmClient.java                     # LLM API client
│   │   └── PromptTemplate.java                # Spring-aware prompts
│   ├── git/                                   # 📦 Git operations
│   │   └── GitService.java                    # JGit wrapper
│   └── github/                                # 🐙 GitHub integration
│       ├── GitHubClient.java                  # GitHub API client
│       └── PullRequestTemplate.java           # PR templates
├── src/main/resources/
│   ├── application.yml                        # ⚙️ Main configuration
│   ├── application-production.yml             # Production overrides
│   └── db/migration/                          # 🗄️ Flyway migrations
├── docs/                                      # 📚 Documentation
│   ├── DEPLOYMENT.md                          # Deployment guide
│   ├── USER_GUIDE.md                          # Usage instructions
│   ├── WEBHOOK_CONFIGURATION.md               # Jenkins integration
│   └── TROUBLESHOOTING.md                     # Common issues
├── docker/                                    # 🐳 Docker configuration
├── docker-compose.yml                         # Service orchestration
└── pom.xml                                    # Maven dependencies
```

## 🔧 Configuration Deep Dive

### Application Configuration (`application.yml`)
```yaml
# Key configuration sections:

# Database connection with environment variable support
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/${POSTGRES_DB:cifixer}
    username: ${POSTGRES_USER:cifixer}
    password: ${POSTGRES_PASSWORD:cifixer}

# LLM API configuration  
llm:
  api:
    base-url: ${LLM_API_BASE_URL:https://openrouter.ai/api/v1}
    key: ${LLM_API_KEY:}
    model: ${LLM_API_MODEL:anthropic/claude-3.5-sonnet}
    max-tokens: ${LLM_API_MAX_TOKENS:128000}
    temperature: 0.1

# GitHub integration
github:
  token: ${GITHUB_TOKEN:}
  api:
    base-url: https://api.github.com

# Security configuration
security:
  webhook:
    signature:
      validation:
        enabled: ${WEBHOOK_SIGNATURE_VALIDATION_ENABLED:false}
    secrets:
      jenkins: ${JENKINS_WEBHOOK_SECRET:}
```

### Docker Compose Services
```yaml
services:
  postgres:      # PostgreSQL database with initialization scripts
  mailhog:       # Email testing (SMTP server + web UI)  
  ci-fixer:      # Main application with health checks
```

## 🔄 How It Works (Complete Flow)

### 1. **Jenkins Build Failure** 
```bash
# Jenkins detects build failure and sends webhook
POST /api/webhook/jenkins
{
  "job": "my-spring-app",
  "buildNumber": 123,
  "branch": "feature/new-feature", 
  "repoUrl": "https://github.com/company/my-app.git",
  "commitSha": "abc123def456",
  "buildLogs": "compilation errors..."
}
```

### 2. **Webhook Processing**
- `WebhookController` validates payload and signature
- Creates `Build` entity in database
- Enqueues initial `PLAN` task
- Returns success response to Jenkins

### 3. **Task Orchestration**
- `Orchestrator` processes tasks from queue
- Dispatches tasks to appropriate agents based on `TaskType`
- Manages task state transitions and retry logic

### 4. **Agent Processing Pipeline**

**a) Planner Agent (`PLAN` task)**
```java
// Analyzes build logs for Spring Boot specific errors
- Parse Maven/Gradle error messages
- Identify error types (compilation, dependency, configuration)
- Extract stack traces and error locations
- Create fix plan with file priorities
```

**b) Retriever Agent (`RETRIEVE` task)**  
```java
// Finds candidate files for fixing
- Clone repository using JGit
- Analyze file relevance using stack traces
- Score files based on error context
- Return ranked list of files to examine
```

**c) Code-Fix Agent (`CODE_FIX` task)**
```java
// Generates code fixes using LLM
- Read current file content
- Create Spring-aware prompts with context
- Call LLM API (OpenRouter/OpenAI) for fixes
- Apply patches and validate syntax
```

**d) Validator Agent (`VALIDATE` task)**
```java  
// Validates generated fixes
- Run Maven/Gradle clean compile
- Execute unit tests  
- Check for new compilation errors
- Return validation results
```

**e) PR Agent (`CREATE_PR` task)**
```java
// Creates GitHub pull request
- Push fix branch to repository
- Generate comprehensive PR description
- Add labels and reviewers
- Link to original build failure
```

### 5. **Notification & Completion**
- `NotificationAgent` sends email updates
- Build status updated to `COMPLETED` or `FAILED`
- Metrics and logs available via actuator endpoints

## 🧪 Testing Strategy

### Test Structure
```
src/test/java/com/example/cifixer/
├── agents/                    # Agent unit tests
├── core/                      # Core framework tests  
├── web/                       # Controller and integration tests
├── store/                     # Repository tests
├── suite/                     # Test suites
└── ApplicationBasicTest.java  # Basic Spring context test
```

### Running Tests
```bash
# Unit tests only (excludes integration tests)
mvn test

# All tests including integration tests  
mvn verify

# Run with coverage
mvn clean test jacoco:report

# Run specific test categories
mvn test -Dtest="**/*UnitTest"
mvn test -Dtest="**/*IntegrationTest"
```

### Test Categories
- **Unit Tests**: Fast, isolated tests with mocks
- **Integration Tests**: Use Testcontainers for real PostgreSQL
- **E2E Tests**: Full workflow testing with MockWebServer
- **Performance Tests**: Load and performance validation

## 🔍 Monitoring & Observability

### Health Checks
```bash
# Application health
curl http://localhost:8081/actuator/health

# Database connection status  
curl http://localhost:8081/actuator/health/db

# Detailed system info
curl http://localhost:8081/actuator/info
```

### Metrics & Monitoring
```bash
# Prometheus metrics
curl http://localhost:8081/actuator/prometheus

# Application metrics
curl http://localhost:8081/actuator/metrics

# Custom business metrics
- cifixer.task.processing.duration
- cifixer.build.processing.duration  
- cifixer.llm.api.requests
- cifixer.github.api.requests
```

### Logging
```yaml
# Structured logging with correlation IDs
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{correlationId:-}] [%X{buildId:-}] [%X{taskId:-}] %logger{36} - %msg%n"
```

## 🛡️ Security Features

### Webhook Security
- **HMAC SHA-256 signature validation** for Jenkins webhooks
- **Input validation and sanitization** for all payloads
- **Rate limiting** on webhook endpoints

### API Security  
- **GitHub token-based authentication** with proper scoping
- **LLM API key protection** via environment variables
- **SSL/TLS verification** for all external API calls

### Code Safety
- **Patch validation** before applying fixes
- **Sandbox execution** for build validation
- **File access restrictions** within working directory

## 🐛 Troubleshooting

### Common Issues

**1. LLM API Connection Failures**
```bash
# Check API key and endpoint
curl -H "Authorization: Bearer $LLM_API_KEY" $LLM_API_BASE_URL/models

# Check application logs
docker-compose logs ci-fixer | grep "LlmClient"
```

**2. GitHub API Rate Limiting**
```bash
# Check rate limit status
curl -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/rate_limit

# Enable rate limit monitoring in logs
```

**3. Build Validation Failures**
```bash
# Check working directory permissions
docker exec ci-fixer-app ls -la /app/work

# Review validation logs
docker-compose logs ci-fixer | grep "ValidatorAgent"
```

**4. Database Connection Issues**
```bash
# Check PostgreSQL container
docker-compose logs postgres

# Verify database connectivity
docker exec ci-fixer-postgres pg_isready -U cifixer
```

### Debug Mode
```bash
# Enable debug logging
export LOG_LEVEL=DEBUG
docker-compose up -d

# Enable LLM request/response logging
export LOGGING_LEVEL_COM_EXAMPLE_CIFIXER_LLM_LLMCLIENT=DEBUG
```

## 📚 Additional Resources

### Documentation
- **[Deployment Guide](docs/DEPLOYMENT.md)** - Production deployment with Docker
- **[User Guide](docs/USER_GUIDE.md)** - End-to-end usage instructions  
- **[Webhook Configuration](docs/WEBHOOK_CONFIGURATION.md)** - Jenkins integration setup
- **[Monitoring Guide](docs/MONITORING.md)** - Observability and metrics
- **[Security Guide](docs/SECURITY.md)** - Security best practices
- **[Troubleshooting](docs/TROUBLESHOOTING.md)** - Common issues and solutions

### Development
- **[Contributing Guidelines](CONTRIBUTING.md)** - How to contribute to the project
- **[API Documentation](docs/API.md)** - REST API reference
- **[Architecture Decision Records](docs/adr/)** - Technical decisions and rationale

## 🤝 Contributing

This project follows standard Spring Boot development practices:

1. **Fork and clone** the repository
2. **Create feature branch** from `master`
3. **Follow code style** and add tests
4. **Run full test suite** before submitting
5. **Create pull request** with descriptive title

### Development Setup
```bash
# Clone repository
git clone https://github.com/uhaseeb85/JenkinsGenieV2.git

# Run in development mode
mvn spring-boot:run -Dspring.profiles.active=dev

# Run tests
mvn clean test
```

---

> **💡 AI Usage Tip**: This README contains comprehensive context about the project architecture, configuration, and operational details. Use this information to understand the codebase structure, troubleshoot issues, and implement new features without starting from scratch each time.