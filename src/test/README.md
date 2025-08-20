# Multi-Agent CI Fixer - Comprehensive Test Suite

This directory contains a comprehensive test suite that validates all requirements and ensures 80%+ code coverage across all components of the Multi-Agent CI Fixer system.

## Test Structure

### Unit Tests
Located in package-specific directories under `src/test/java/com/example/cifixer/`:

- **agents/**: Tests for all agent implementations (PlannerAgent, CodeFixAgent, etc.)
- **core/**: Tests for core services (TaskQueueService, RetryHandler, etc.)
- **git/**: Tests for Git operations (RepoAgent)
- **github/**: Tests for GitHub integration (GitHubClient, PrAgent)
- **llm/**: Tests for LLM integration (LlmClient, prompt templates)
- **notification/**: Tests for notification system
- **store/**: Tests for JPA entities and repositories
- **util/**: Tests for utility classes (SpringProjectAnalyzer, CommandExecutor)
- **web/**: Tests for web controllers and webhook handling

### Integration Tests
- **integration/**: Full integration tests using Testcontainers with PostgreSQL
- **store/**: Database integration tests with real PostgreSQL instances

### End-to-End Tests
- **e2e/**: Complete workflow tests with various Spring Boot error scenarios

### Performance Tests
- **performance/**: Load and performance tests for task queue processing

### Test Support
- **fixtures/**: Test data factories and builders
- **config/**: Test configuration and mock setups
- **suite/**: Test suite runners and comprehensive validation

## Running Tests

### Prerequisites
- Java 8+
- Maven 3.6+
- Docker (for integration tests with Testcontainers)

### Quick Start
```bash
# Run all tests with coverage
./run-tests.sh

# Windows
run-tests.bat

# Run with performance tests
./run-tests.sh --performance

# Run comprehensive suite
./run-tests.sh --comprehensive
```

### Maven Commands
```bash
# Unit tests only
mvn test

# Integration tests
mvn verify

# Generate coverage report
mvn jacoco:report

# Check coverage requirements (80% minimum)
mvn jacoco:check

# Run specific test categories
mvn test -Dtest="**/*Test"
mvn test -Dtest="**/*IntegrationTest"
mvn test -Dtest="**/*PerformanceTest"
```

## Test Categories

### 1. Unit Tests
- **Coverage Target**: 80%+ line coverage, 75%+ branch coverage
- **Focus**: Individual component behavior, mocking external dependencies
- **Examples**:
  - `PlannerAgentTest`: Tests Spring Boot log parsing and plan generation
  - `CodeFixAgentTest`: Tests LLM integration and patch application
  - `TaskQueueServiceTest`: Tests concurrent task processing

### 2. Integration Tests
- **Focus**: Component interaction, database operations, external API integration
- **Uses**: Testcontainers for PostgreSQL, MockWebServer for HTTP APIs
- **Examples**:
  - `ComprehensiveIntegrationTest`: Full workflow from webhook to PR creation
  - `DatabaseIntegrationTest`: JPA repository operations with real database

### 3. End-to-End Tests
- **Focus**: Complete user scenarios with realistic Spring Boot error cases
- **Examples**:
  - `SpringBootErrorScenariosTest`: Tests various Maven/Gradle compilation errors
  - Maven dependency conflicts, Spring context failures, test failures

### 4. Performance Tests
- **Focus**: System performance under load, memory usage, latency
- **Examples**:
  - `TaskQueuePerformanceTest`: Concurrent task processing, retry mechanisms
  - Load testing with 1000+ tasks, memory usage validation

## Test Data and Fixtures

### TestDataFactory
Provides factory methods for creating realistic test data:
- `createBuild()`: Creates Build entities with realistic Spring Boot project data
- `createTask()`: Creates Task entities for different task types
- `createSpringBootErrorLogs()`: Generates realistic Maven/Gradle error logs
- `createPatchPayload()`: Creates patch payloads with Spring context

### Test Configuration
- **application-test.yml**: Test-specific configuration
- **TestConfiguration.class**: Mock beans and test setup
- **Testcontainers**: PostgreSQL containers for integration tests

## Coverage Requirements

The test suite enforces the following coverage requirements:
- **Instruction Coverage**: 80% minimum
- **Branch Coverage**: 75% minimum

Coverage is measured using JaCoCo and reports are generated at:
- `target/site/jacoco/index.html`

## Requirements Validation

### RequirementsValidationTest
Validates that all 10 requirements from the requirements document are implemented:

1. **Requirement 1**: Jenkins webhook handling ✓
2. **Requirement 2**: Log analysis and planning ✓
3. **Requirement 3**: File retrieval and ranking ✓
4. **Requirement 4**: LLM-based patch generation ✓
5. **Requirement 5**: Build validation ✓
6. **Requirement 6**: GitHub PR creation ✓
7. **Requirement 7**: Notification system ✓
8. **Requirement 8**: Spring Boot deployment ✓
9. **Requirement 9**: Security and input validation ✓
10. **Requirement 10**: Error handling and retry logic ✓

## Spring Boot Error Scenarios Tested

The test suite covers various Spring Boot error types:

### Compilation Errors
- Missing class imports
- Symbol not found errors
- Maven/Gradle compilation failures

### Spring Context Errors
- `NoSuchBeanDefinitionException`
- Missing `@Repository`, `@Service`, `@Component` annotations
- Autowiring failures

### Test Failures
- JUnit test failures
- Spring Boot test context issues
- Maven Surefire/Failsafe failures

### Dependency Errors
- Maven dependency resolution failures
- Version conflicts
- Missing dependencies

### Build Tool Errors
- Maven compilation plugin failures
- Gradle build script errors
- Multi-module project issues

## Mock Configurations

### External Services
- **LLM Server**: MockWebServer simulating Ollama/LM Studio responses
- **GitHub API**: MockWebServer for PR creation and repository operations
- **SMTP Server**: GreenMail for email notification testing
- **Command Execution**: Mocked Maven/Gradle command execution

### Database
- **Unit Tests**: H2 in-memory database
- **Integration Tests**: PostgreSQL via Testcontainers
- **Performance Tests**: PostgreSQL with connection pooling

## Continuous Integration

The test suite is designed to run in CI/CD environments:
- Docker support for Testcontainers
- Parallel test execution
- Comprehensive reporting
- Coverage enforcement
- Performance benchmarking

## Troubleshooting

### Common Issues

1. **Testcontainers fails to start**
   - Ensure Docker is running
   - Check Docker permissions
   - Verify network connectivity

2. **Coverage requirements not met**
   - Run `mvn jacoco:report` to see detailed coverage
   - Add tests for uncovered code paths
   - Check exclusions in JaCoCo configuration

3. **Integration tests timeout**
   - Increase timeout values in test configuration
   - Check system resources
   - Verify external service availability

4. **Performance tests fail**
   - Adjust performance thresholds
   - Check system load during test execution
   - Verify database connection pool settings

### Debug Mode
```bash
# Run tests with debug logging
mvn test -Dlogging.level.com.example.cifixer=DEBUG

# Run specific test with debug
mvn test -Dtest="PlannerAgentTest" -Dlogging.level.root=DEBUG
```

## Contributing

When adding new tests:
1. Follow the existing package structure
2. Use TestDataFactory for test data creation
3. Mock external dependencies appropriately
4. Ensure tests are deterministic and isolated
5. Add performance tests for new critical paths
6. Update this README with new test categories

## Reports

After running tests, the following reports are available:
- **Coverage**: `target/site/jacoco/index.html`
- **Surefire**: `target/surefire-reports/`
- **Failsafe**: `target/failsafe-reports/`
- **Performance**: Console output with metrics