# Multi-Agent CI Fixer - User Guide

## Overview

The Multi-Agent CI Fixer is an automated system that monitors Jenkins build failures for Java Spring projects and automatically generates fixes using AI. This guide covers how to use the system effectively and understand its capabilities.

## How It Works

### 1. Build Failure Detection

When a Jenkins build fails, the system:
1. Receives a webhook notification with build details and logs
2. Analyzes the failure to understand what went wrong
3. Creates a structured plan for fixing the issues
4. Automatically generates and applies code fixes
5. Validates the fixes by running tests
6. Creates a GitHub pull request with the proposed changes
7. Notifies stakeholders about the results

### 2. Supported Project Types

The system is specifically designed for:
- **Java Spring Boot projects** (versions 2.x and 3.x)
- **Maven-based projects** (pom.xml)
- **Gradle-based projects** (build.gradle)
- **GitHub-hosted repositories**

### 3. Supported Error Types

The system can automatically fix:

#### Compilation Errors
- Missing imports
- Undefined variables or methods
- Type mismatches
- Syntax errors

#### Spring Framework Issues
- Missing `@Component`, `@Service`, `@Repository`, `@Controller` annotations
- Dependency injection problems
- Bean configuration issues
- Spring Boot configuration errors

#### Dependency Problems
- Missing Maven/Gradle dependencies
- Version conflicts
- Classpath issues

#### Test Failures
- Simple unit test fixes
- Mock configuration issues
- Test data problems

## Getting Started

### 1. Prerequisites

Before using the CI Fixer, ensure:
- Your project is a Java Spring Boot project using Maven or Gradle
- Your repository is hosted on GitHub
- You have Jenkins configured for CI/CD
- The CI Fixer system is deployed and accessible

### 2. Configure Your Jenkins Job

#### Option A: Automatic Webhook (Recommended)

Add a post-build action to send webhooks on failure:

```groovy
pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }
        
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }
    }
    
    post {
        failure {
            script {
                // Send webhook to CI Fixer
                def payload = [
                    job: env.JOB_NAME,
                    buildNumber: env.BUILD_NUMBER.toInteger(),
                    branch: env.GIT_BRANCH,
                    repoUrl: env.GIT_URL,
                    commitSha: env.GIT_COMMIT,
                    logs: currentBuild.rawBuild.getLog(300).join('\n').bytes.encodeBase64().toString(),
                    status: 'FAILURE',
                    timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
                ]
                
                httpRequest(
                    httpMode: 'POST',
                    url: 'https://ci-fixer.yourcompany.com/webhooks/jenkins',
                    contentType: 'APPLICATION_JSON',
                    requestBody: groovy.json.JsonOutput.toJson(payload),
                    customHeaders: [
                        [name: 'X-Jenkins-Signature', value: "sha256=${generateSignature(payload)}"]
                    ]
                )
            }
        }
    }
}
```

#### Option B: Manual Trigger

You can also manually trigger the CI Fixer by sending a webhook:

```bash
curl -X POST https://ci-fixer.yourcompany.com/webhooks/jenkins \
  -H "Content-Type: application/json" \
  -H "X-Jenkins-Signature: sha256=your_signature" \
  -d '{
    "job": "my-spring-project",
    "buildNumber": 123,
    "branch": "main",
    "repoUrl": "https://github.com/myorg/my-spring-project.git",
    "commitSha": "abc123def456",
    "logs": "base64_encoded_build_logs",
    "status": "FAILURE",
    "timestamp": "2024-01-15T10:30:00Z"
  }'
```

### 3. Monitor Processing

#### Check Build Status

Visit the CI Fixer dashboard to monitor build processing:

```bash
# Check overall system health
curl https://ci-fixer.yourcompany.com/actuator/health

# Check specific build status
curl https://ci-fixer.yourcompany.com/builds/123
```

#### View Processing Timeline

The system provides a timeline of processing steps:

1. **RECEIVED** - Webhook received and validated
2. **PLANNING** - Analyzing build logs and creating fix plan
3. **RETRIEVING** - Identifying relevant source files
4. **PATCHING** - Generating and applying code fixes
5. **VALIDATING** - Running tests to verify fixes
6. **PR_CREATING** - Creating GitHub pull request
7. **COMPLETED** - Process finished successfully
8. **FAILED** - Process failed (manual intervention needed)

## Understanding the Fix Process

### 1. Log Analysis

The system analyzes your build logs to identify:

#### Maven Compilation Errors
```
[ERROR] /src/main/java/com/example/UserService.java:[15,8] cannot find symbol
  symbol:   class UserRepository
  location: class com.example.service.UserService
```

**Fix:** Add missing import or dependency injection annotation

#### Spring Context Errors
```
NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.repository.UserRepository'
```

**Fix:** Add `@Repository` annotation to UserRepository class

#### Test Failures
```
java.lang.AssertionError: Expected: <5> but was: <0>
    at com.example.UserServiceTest.testGetUserCount(UserServiceTest.java:25)
```

**Fix:** Correct test logic or mock configuration

### 2. File Ranking

The system ranks files by relevance to fix the issue:

1. **High Priority:** Files mentioned in stack traces
2. **Medium Priority:** Spring components related to the error
3. **Low Priority:** Files with lexical matches to error terms

### 3. Code Generation

The system uses AI to generate minimal, targeted fixes:

#### Example: Missing Annotation Fix

**Before:**
```java
public class UserRepository {
    public User findById(Long id) {
        // implementation
    }
}
```

**Generated Fix:**
```java
@Repository
public class UserRepository {
    public User findById(Long id) {
        // implementation
    }
}
```

#### Example: Missing Dependency Fix

**Before (pom.xml):**
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

**Generated Fix:**
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

### 4. Validation Process

Before creating a pull request, the system validates fixes by:

1. **Compilation Check:** Ensures code compiles without errors
2. **Test Execution:** Runs unit and integration tests
3. **Spring Context Loading:** Verifies Spring application starts correctly
4. **Safety Validation:** Ensures no dangerous operations were introduced

## Working with Pull Requests

### 1. Pull Request Format

The CI Fixer creates pull requests with:

#### Title Format
```
Fix: Jenkins build #123 (abc123d)
```

#### Description Template
```markdown
## Automated Fix for Build Failure

**Build:** #123
**Branch:** main
**Commit:** abc123def456
**Jenkins URL:** https://jenkins.company.com/job/my-project/123/

### Issues Identified
- Missing @Repository annotation on UserRepository class
- Missing spring-boot-starter-data-jpa dependency

### Changes Made
- Added @Repository annotation to UserRepository.java
- Added spring-boot-starter-data-jpa dependency to pom.xml

### Validation Results
✅ Compilation: PASSED
✅ Tests: PASSED (15/15)
✅ Spring Context: LOADED

### Files Changed
- `src/main/java/com/example/repository/UserRepository.java`
- `pom.xml`

---
*This PR was automatically generated by CI Fixer*
```

### 2. Reviewing Pull Requests

#### What to Check

1. **Understand the Changes:** Review the diff to understand what was modified
2. **Verify the Fix:** Ensure the changes address the original build failure
3. **Check for Side Effects:** Look for any unintended consequences
4. **Test Locally:** Pull the branch and test manually if needed

#### Common Review Points

- Are the changes minimal and focused?
- Do the changes follow your team's coding standards?
- Are there any security implications?
- Should additional tests be added?

### 3. Merging Pull Requests

#### Automatic Merge (Optional)

You can configure automatic merging for simple fixes:

```yaml
# .github/workflows/auto-merge-ci-fixer.yml
name: Auto-merge CI Fixer PRs

on:
  pull_request:
    types: [opened]

jobs:
  auto-merge:
    if: contains(github.event.pull_request.title, 'Fix: Jenkins build') && github.event.pull_request.user.login == 'ci-fixer-bot'
    runs-on: ubuntu-latest
    
    steps:
      - name: Check CI status
        uses: actions/github-script@v6
        with:
          script: |
            const { data: checks } = await github.rest.checks.listForRef({
              owner: context.repo.owner,
              repo: context.repo.repo,
              ref: context.payload.pull_request.head.sha
            });
            
            const allPassed = checks.check_runs.every(check => check.conclusion === 'success');
            if (!allPassed) {
              core.setFailed('Not all checks passed');
            }
      
      - name: Auto-merge
        uses: pascalgn/merge-action@v0.15.6
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          merge_method: squash
```

#### Manual Review Process

For teams preferring manual review:

1. **Assign Reviewers:** Automatically assign team members
2. **Required Approvals:** Require at least one approval
3. **Branch Protection:** Use branch protection rules
4. **Merge Strategy:** Use squash merge to maintain clean history

## Monitoring and Notifications

### 1. Email Notifications

The system sends email notifications for:

#### Successful Fixes
```
Subject: ✅ CI Fixer: Build #123 fixed automatically

The CI Fixer has successfully fixed build #123 for my-spring-project.

Pull Request: https://github.com/myorg/my-spring-project/pull/456
Jenkins Build: https://jenkins.company.com/job/my-project/123/

Changes:
- Added @Repository annotation to UserRepository.java
- Added missing dependency to pom.xml

The fix has been validated and is ready for review.
```

#### Failed Attempts
```
Subject: ❌ CI Fixer: Build #123 requires manual intervention

The CI Fixer was unable to automatically fix build #123 for my-spring-project.

Jenkins Build: https://jenkins.company.com/job/my-project/123/
Error Details: Complex dependency conflict requires manual resolution

Please review the build logs and fix manually.
```

### 2. Dashboard Monitoring

Access the CI Fixer dashboard to monitor:

- **Recent Builds:** List of processed builds and their status
- **Success Rate:** Percentage of builds fixed automatically
- **Processing Time:** Average time to generate fixes
- **Common Issues:** Most frequent types of build failures

### 3. Metrics and Analytics

Track system performance with metrics:

```bash
# Success rate over time
curl https://ci-fixer.yourcompany.com/actuator/metrics/cifixer.builds.success.rate

# Average processing time
curl https://ci-fixer.yourcompany.com/actuator/metrics/cifixer.processing.duration

# Most common error types
curl https://ci-fixer.yourcompany.com/admin/analytics/error-types
```

## Best Practices

### 1. Project Setup

#### Optimize for CI Fixer

- **Clear Error Messages:** Use descriptive error messages in your code
- **Consistent Structure:** Follow standard Maven/Gradle project structure
- **Good Test Coverage:** Write comprehensive tests to validate fixes
- **Proper Logging:** Use structured logging for better error analysis

#### Example: Good Error Handling
```java
@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository cannot be null");
    }
    
    public User findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
    }
}
```

### 2. Team Workflow

#### Integration Strategy

1. **Start Small:** Begin with non-critical projects
2. **Monitor Closely:** Review all generated PRs initially
3. **Build Trust:** Gradually increase automation as confidence grows
4. **Team Training:** Ensure team understands the system capabilities

#### Code Review Process

1. **Automated Checks:** Ensure CI passes before review
2. **Quick Review:** Focus on correctness and safety
3. **Learning Opportunity:** Use PRs to understand common issues
4. **Feedback Loop:** Report issues to improve the system

### 3. Troubleshooting

#### When Fixes Don't Work

1. **Check Logs:** Review CI Fixer logs for error details
2. **Verify Configuration:** Ensure project follows supported patterns
3. **Manual Fix:** Apply manual fix and analyze the difference
4. **Report Issues:** Help improve the system by reporting edge cases

#### Common Limitations

- **Complex Logic Errors:** Requires human understanding
- **Architecture Changes:** Major refactoring beyond scope
- **External Dependencies:** Issues with third-party services
- **Business Logic:** Domain-specific requirements

## Advanced Usage

### 1. Custom Configuration

#### Project-Specific Settings

Create `.ci-fixer.yml` in your repository root:

```yaml
# CI Fixer configuration
ci-fixer:
  # File patterns to include/exclude
  include-patterns:
    - "src/main/java/**/*.java"
    - "src/test/java/**/*.java"
    - "pom.xml"
    - "build.gradle"
  
  exclude-patterns:
    - "src/main/java/**/generated/**"
    - "target/**"
    - "build/**"
  
  # Validation settings
  validation:
    run-tests: true
    test-timeout: 300  # seconds
    max-patch-size: 500  # lines
  
  # Notification settings
  notifications:
    email:
      - "team-lead@company.com"
      - "devops@company.com"
    slack:
      webhook: "https://hooks.slack.com/services/..."
      channel: "#ci-alerts"
```

#### Branch-Specific Behavior

```yaml
ci-fixer:
  branches:
    main:
      auto-merge: false
      require-review: true
    develop:
      auto-merge: true
      require-review: false
    feature/*:
      enabled: true
      create-pr: true
```

### 2. Integration with Other Tools

#### Slack Integration

```yaml
# Add to .ci-fixer.yml
notifications:
  slack:
    webhook: "${SLACK_WEBHOOK_URL}"
    channels:
      success: "#ci-success"
      failure: "#ci-alerts"
    message-template: |
      🤖 CI Fixer Update
      Project: {project}
      Build: #{buildNumber}
      Status: {status}
      PR: {prUrl}
```

#### JIRA Integration

```yaml
# Add to .ci-fixer.yml
integrations:
  jira:
    base-url: "https://company.atlassian.net"
    project-key: "PROJ"
    create-ticket: true
    ticket-template: |
      Automated fix applied for build failure
      
      Build: #{buildNumber}
      Branch: {branch}
      Commit: {commitSha}
      
      Changes: {changesSummary}
```

## FAQ

### General Questions

**Q: What types of projects does CI Fixer support?**
A: Java Spring Boot projects using Maven or Gradle, hosted on GitHub.

**Q: How long does it take to generate a fix?**
A: Typically 2-5 minutes, depending on project size and complexity.

**Q: Can I disable CI Fixer for specific builds?**
A: Yes, add `[skip ci-fixer]` to your commit message.

### Technical Questions

**Q: What happens if the generated fix is wrong?**
A: The validation step will catch most issues. If a bad fix gets through, simply close the PR and report the issue.

**Q: Can CI Fixer handle multiple build failures?**
A: Yes, it processes each build independently and can handle multiple failures simultaneously.

**Q: Does CI Fixer work with private repositories?**
A: Yes, as long as the system has proper access tokens configured.

### Security Questions

**Q: Is my code secure with CI Fixer?**
A: Yes, the system uses secure practices including webhook signature validation, input sanitization, and patch safety checks.

**Q: What data does CI Fixer store?**
A: Build metadata, logs, and generated patches. No source code is permanently stored.

**Q: Can I audit CI Fixer actions?**
A: Yes, all actions are logged and can be audited through the dashboard and logs.

## Support and Resources

### Getting Help

1. **Documentation:** Check this guide and other documentation
2. **Logs:** Review CI Fixer logs for error details
3. **Dashboard:** Use the web dashboard for monitoring
4. **Team Lead:** Contact your team lead or DevOps team

### Reporting Issues

When reporting issues, include:
- Build number and project name
- Error messages from logs
- Expected vs actual behavior
- Steps to reproduce

### Contributing

Help improve CI Fixer by:
- Reporting edge cases and limitations
- Suggesting new error types to support
- Providing feedback on generated fixes
- Contributing to documentation

For more technical information, see:
- [Deployment Guide](DEPLOYMENT.md)
- [Webhook Configuration](WEBHOOK_CONFIGURATION.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [Security Best Practices](SECURITY.md)