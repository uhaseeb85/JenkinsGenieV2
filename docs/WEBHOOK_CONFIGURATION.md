# Webhook Configuration Guide

## Overview

The Multi-Agent CI Fixer receives build failure notifications through webhooks from Jenkins. This guide covers setting up webhooks for different CI/CD systems and configuring the application to receive them securely.

## Jenkins Configuration

### 1. Install Required Plugins

Install the following Jenkins plugins:
- **Generic Webhook Trigger Plugin** - For flexible webhook configuration
- **HTTP Request Plugin** - For making HTTP calls to the CI Fixer

### 2. Configure Webhook in Jenkins Job

#### Option A: Using Generic Webhook Trigger Plugin

1. In your Jenkins job configuration, go to **Build Triggers**
2. Check **Generic Webhook Trigger**
3. Configure the following:

**Post content parameters:**
```
Variable: job
Expression: $.job
JSONPath: $.job

Variable: buildNumber
Expression: $.buildNumber
JSONPath: $.buildNumber

Variable: branch
Expression: $.branch
JSONPath: $.branch

Variable: repoUrl
Expression: $.repoUrl
JSONPath: $.repoUrl

Variable: commitSha
Expression: $.commitSha
JSONPath: $.commitSha

Variable: logs
Expression: $.logs
JSONPath: $.logs
```

**Token:** `your-webhook-token` (configure this in your CI Fixer environment)

**Webhook URL:** `http://your-ci-fixer:8080/webhooks/jenkins`

#### Option B: Using Post-Build Action

1. Add **Post-build Actions** → **HTTP Request**
2. Configure for build failures only
3. Set URL: `http://your-ci-fixer:8080/webhooks/jenkins`
4. Set HTTP Mode: `POST`
5. Add the following content:

```json
{
  "job": "${JOB_NAME}",
  "buildNumber": ${BUILD_NUMBER},
  "branch": "${GIT_BRANCH}",
  "repoUrl": "${GIT_URL}",
  "commitSha": "${GIT_COMMIT}",
  "logs": "${BUILD_LOG}",
  "status": "FAILURE",
  "timestamp": "${BUILD_TIMESTAMP}"
}
```

### 3. Configure Webhook Security

#### Generate Webhook Secret

```bash
# Generate a secure webhook secret
openssl rand -hex 32
```

Add this secret to both Jenkins and CI Fixer configuration.

#### Jenkins Configuration

In Jenkins, add the secret as a credential and use it in the webhook configuration:

```groovy
// In Jenkins Pipeline
pipeline {
    agent any
    
    post {
        failure {
            script {
                def webhookSecret = credentials('ci-fixer-webhook-secret')
                def payload = [
                    job: env.JOB_NAME,
                    buildNumber: env.BUILD_NUMBER,
                    branch: env.GIT_BRANCH,
                    repoUrl: env.GIT_URL,
                    commitSha: env.GIT_COMMIT,
                    logs: currentBuild.rawBuild.getLog(300).join('\n'),
                    status: 'FAILURE',
                    timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
                ]
                
                def payloadJson = groovy.json.JsonOutput.toJson(payload)
                def signature = generateHmacSha256(payloadJson, webhookSecret)
                
                httpRequest(
                    httpMode: 'POST',
                    url: 'http://your-ci-fixer:8080/webhooks/jenkins',
                    contentType: 'APPLICATION_JSON',
                    requestBody: payloadJson,
                    customHeaders: [
                        [name: 'X-Jenkins-Signature', value: "sha256=${signature}"]
                    ]
                )
            }
        }
    }
}

def generateHmacSha256(String data, String key) {
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256")
    javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256")
    mac.init(secretKeySpec)
    return mac.doFinal(data.getBytes()).encodeHex().toString()
}
```

## GitHub Actions Configuration

### Workflow Configuration

Create `.github/workflows/ci-fixer-webhook.yml`:

```yaml
name: CI Fixer Webhook

on:
  workflow_run:
    workflows: ["CI"]  # Name of your main CI workflow
    types:
      - completed

jobs:
  notify-ci-fixer:
    runs-on: ubuntu-latest
    if: ${{ github.event.workflow_run.conclusion == 'failure' }}
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v3
        with:
          ref: ${{ github.event.workflow_run.head_sha }}
      
      - name: Get build logs
        id: logs
        run: |
          # Download logs from failed workflow
          gh run download ${{ github.event.workflow_run.id }} --name build-logs || echo "No logs available"
          LOGS=$(cat build-logs.txt 2>/dev/null | tail -300 | base64 -w 0 || echo "")
          echo "logs=$LOGS" >> $GITHUB_OUTPUT
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Send webhook to CI Fixer
        run: |
          PAYLOAD=$(cat << EOF
          {
            "job": "${{ github.repository }}",
            "buildNumber": ${{ github.event.workflow_run.run_number }},
            "branch": "${{ github.event.workflow_run.head_branch }}",
            "repoUrl": "${{ github.event.repository.clone_url }}",
            "commitSha": "${{ github.event.workflow_run.head_sha }}",
            "logs": "${{ steps.logs.outputs.logs }}",
            "status": "FAILURE",
            "timestamp": "${{ github.event.workflow_run.created_at }}"
          }
          EOF
          )
          
          SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "${{ secrets.CI_FIXER_WEBHOOK_SECRET }}" | sed 's/^.* //')
          
          curl -X POST \
            -H "Content-Type: application/json" \
            -H "X-Jenkins-Signature: sha256=$SIGNATURE" \
            -d "$PAYLOAD" \
            "${{ secrets.CI_FIXER_WEBHOOK_URL }}"
        env:
          CI_FIXER_WEBHOOK_SECRET: ${{ secrets.CI_FIXER_WEBHOOK_SECRET }}
          CI_FIXER_WEBHOOK_URL: ${{ secrets.CI_FIXER_WEBHOOK_URL }}
```

### Required Secrets

Add these secrets to your GitHub repository:

- `CI_FIXER_WEBHOOK_SECRET` - The webhook secret key
- `CI_FIXER_WEBHOOK_URL` - The CI Fixer webhook URL (e.g., `https://ci-fixer.yourcompany.com/webhooks/jenkins`)

## GitLab CI Configuration

### .gitlab-ci.yml Configuration

```yaml
stages:
  - build
  - test
  - notify

variables:
  CI_FIXER_URL: "https://ci-fixer.yourcompany.com/webhooks/jenkins"

build:
  stage: build
  script:
    - mvn clean compile
  artifacts:
    when: on_failure
    reports:
      junit: target/surefire-reports/TEST-*.xml
    paths:
      - target/

test:
  stage: test
  script:
    - mvn test
  artifacts:
    when: on_failure
    reports:
      junit: target/surefire-reports/TEST-*.xml

notify_ci_fixer:
  stage: notify
  image: curlimages/curl:latest
  script:
    - |
      if [ "$CI_JOB_STATUS" = "failed" ]; then
        LOGS=$(cat $CI_PROJECT_DIR/build.log | tail -300 | base64 -w 0 2>/dev/null || echo "")
        PAYLOAD=$(cat << EOF
        {
          "job": "$CI_PROJECT_PATH",
          "buildNumber": $CI_PIPELINE_ID,
          "branch": "$CI_COMMIT_REF_NAME",
          "repoUrl": "$CI_REPOSITORY_URL",
          "commitSha": "$CI_COMMIT_SHA",
          "logs": "$LOGS",
          "status": "FAILURE",
          "timestamp": "$CI_PIPELINE_CREATED_AT"
        }
        EOF
        )
        
        SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$CI_FIXER_WEBHOOK_SECRET" | sed 's/^.* //')
        
        curl -X POST \
          -H "Content-Type: application/json" \
          -H "X-Jenkins-Signature: sha256=$SIGNATURE" \
          -d "$PAYLOAD" \
          "$CI_FIXER_URL"
      fi
  when: on_failure
  variables:
    CI_FIXER_WEBHOOK_SECRET: $CI_FIXER_WEBHOOK_SECRET
```

## CI Fixer Application Configuration

### Environment Variables

Configure the following environment variables in your CI Fixer deployment:

```bash
# Webhook Security
WEBHOOK_SECRET=your_webhook_secret_key_here

# Optional: Disable signature validation for testing
WEBHOOK_SIGNATURE_VALIDATION_ENABLED=true

# GitHub Integration
GITHUB_TOKEN=ghp_your_github_token_here
GITHUB_BASE_URL=https://api.github.com

# Repository Access
GIT_USERNAME=your_git_username
GIT_TOKEN=your_git_token
```

### Application Properties

In `application.yml`:

```yaml
webhook:
  signature:
    validation:
      enabled: ${WEBHOOK_SIGNATURE_VALIDATION_ENABLED:true}
  secret: ${WEBHOOK_SECRET}

github:
  token: ${GITHUB_TOKEN}
  base-url: ${GITHUB_BASE_URL:https://api.github.com}

git:
  username: ${GIT_USERNAME:}
  token: ${GIT_TOKEN:}
```

## Testing Webhook Configuration

### 1. Test Webhook Endpoint

```bash
# Test basic connectivity
curl -X GET http://your-ci-fixer:8080/actuator/health

# Test webhook endpoint (should return 400 without proper payload)
curl -X POST http://your-ci-fixer:8080/webhooks/jenkins
```

### 2. Send Test Webhook

Create a test script to send a sample webhook:

```bash
#!/bin/bash

WEBHOOK_URL="http://your-ci-fixer:8080/webhooks/jenkins"
WEBHOOK_SECRET="your_webhook_secret"

PAYLOAD='{
  "job": "test-project",
  "buildNumber": 123,
  "branch": "main",
  "repoUrl": "https://github.com/yourorg/test-project.git",
  "commitSha": "abc123def456",
  "logs": "W0VSUk9SXSBDb21waWxhdGlvbiBmYWlsZWQ=",
  "status": "FAILURE",
  "timestamp": "2024-01-15T10:30:00Z"
}'

SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | sed 's/^.* //')

curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-Jenkins-Signature: sha256=$SIGNATURE" \
  -d "$PAYLOAD" \
  "$WEBHOOK_URL"
```

### 3. Verify Processing

Check the CI Fixer logs to verify the webhook was processed:

```bash
# Check application logs
docker-compose logs -f app

# Check database for new build record
docker-compose exec db psql -U cifixer -d cifixer -c "SELECT * FROM builds ORDER BY created_at DESC LIMIT 5;"

# Check task queue
docker-compose exec db psql -U cifixer -d cifixer -c "SELECT * FROM tasks WHERE build_id = (SELECT id FROM builds ORDER BY created_at DESC LIMIT 1);"
```

## Webhook Payload Format

### Required Fields

The webhook payload must include the following fields:

```json
{
  "job": "string",           // Job/project name
  "buildNumber": "integer",  // Build number
  "branch": "string",        // Git branch name
  "repoUrl": "string",       // Repository URL
  "commitSha": "string",     // Git commit SHA
  "logs": "string",          // Base64 encoded build logs
  "status": "string",        // Build status (FAILURE)
  "timestamp": "string"      // ISO 8601 timestamp
}
```

### Optional Fields

```json
{
  "buildUrl": "string",      // Link to build in CI system
  "author": "string",        // Commit author
  "message": "string",       // Commit message
  "duration": "integer",     // Build duration in seconds
  "testResults": {           // Test result summary
    "total": "integer",
    "failed": "integer",
    "skipped": "integer"
  }
}
```

## Security Considerations

### HMAC Signature Validation

The CI Fixer validates webhook signatures using HMAC-SHA256:

1. Generate signature: `HMAC-SHA256(payload, secret)`
2. Send in header: `X-Jenkins-Signature: sha256=<signature>`
3. CI Fixer validates signature matches

### Network Security

- Use HTTPS for webhook URLs in production
- Restrict webhook endpoint access using firewall rules
- Consider using VPN or private networks for CI system communication

### Secret Management

- Store webhook secrets securely (environment variables, secret management systems)
- Rotate webhook secrets regularly
- Use different secrets for different environments

## Troubleshooting

### Common Issues

1. **Webhook not received**
   - Check network connectivity
   - Verify webhook URL is correct
   - Check firewall rules

2. **Signature validation failed**
   - Verify webhook secret matches
   - Check signature generation algorithm
   - Ensure payload is not modified in transit

3. **Invalid payload format**
   - Verify required fields are present
   - Check JSON format is valid
   - Ensure logs are properly base64 encoded

### Debug Mode

Enable debug logging to troubleshoot webhook issues:

```yaml
logging:
  level:
    com.example.cifixer.web.WebhookController: DEBUG
    com.example.cifixer.web.WebhookValidator: DEBUG
```

### Webhook Testing Tools

Use these tools to test webhook configuration:

- **ngrok** - Expose local CI Fixer for testing
- **Postman** - Send test webhook requests
- **curl** - Command-line webhook testing
- **Webhook.site** - Online webhook testing service

## Integration Examples

### Complete Jenkins Pipeline Example

```groovy
pipeline {
    agent any
    
    environment {
        CI_FIXER_URL = 'https://ci-fixer.yourcompany.com/webhooks/jenkins'
        CI_FIXER_SECRET = credentials('ci-fixer-webhook-secret')
    }
    
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
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
    }
    
    post {
        failure {
            script {
                def logs = currentBuild.rawBuild.getLog(300).join('\n')
                def payload = [
                    job: env.JOB_NAME,
                    buildNumber: env.BUILD_NUMBER.toInteger(),
                    branch: env.GIT_BRANCH,
                    repoUrl: env.GIT_URL,
                    commitSha: env.GIT_COMMIT,
                    logs: logs.bytes.encodeBase64().toString(),
                    status: 'FAILURE',
                    timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'"),
                    buildUrl: env.BUILD_URL,
                    author: sh(script: 'git log -1 --pretty=format:"%an"', returnStdout: true).trim(),
                    message: sh(script: 'git log -1 --pretty=format:"%s"', returnStdout: true).trim()
                ]
                
                sendWebhook(payload, env.CI_FIXER_SECRET, env.CI_FIXER_URL)
            }
        }
    }
}

def sendWebhook(payload, secret, url) {
    def payloadJson = groovy.json.JsonOutput.toJson(payload)
    def signature = generateHmacSha256(payloadJson, secret)
    
    httpRequest(
        httpMode: 'POST',
        url: url,
        contentType: 'APPLICATION_JSON',
        requestBody: payloadJson,
        customHeaders: [
            [name: 'X-Jenkins-Signature', value: "sha256=${signature}"]
        ],
        validResponseCodes: '200:299'
    )
}

def generateHmacSha256(String data, String key) {
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256")
    javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256")
    mac.init(secretKeySpec)
    return mac.doFinal(data.getBytes()).encodeHex().toString()
}
```

For more information, see the [Deployment Guide](DEPLOYMENT.md) and [Troubleshooting Guide](TROUBLESHOOTING.md).