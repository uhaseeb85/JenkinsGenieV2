# CI Fixer Troubleshooting Guide

This guide helps diagnose and resolve common issues with the CI Fixer Docker deployment.

## Quick Diagnostics

### Check Service Status

```bash
# Check all services
docker-compose ps

# Check specific service health
docker-compose exec ci-fixer wget -qO- http://localhost:8080/actuator/health

# Check logs for errors
docker-compose logs --tail=50 ci-fixer
```

### Verify Configuration

```bash
# Check environment variables
docker-compose config

# Validate docker-compose.yml syntax
docker-compose config --quiet
```

## Common Issues

### 1. Application Won't Start

**Symptoms:**
- Container exits immediately
- "Connection refused" errors
- Health check failures

**Diagnosis:**
```bash
# Check application logs
docker-compose logs ci-fixer

# Check if database is ready
docker-compose exec postgres pg_isready -U cifixer

# Verify environment variables
docker-compose exec ci-fixer env | grep -E "(SPRING|POSTGRES|GITHUB)"
```

**Solutions:**

**Database Connection Issues:**
```bash
# Ensure database is healthy
docker-compose up -d postgres
docker-compose logs postgres

# Check database credentials
docker-compose exec postgres psql -U cifixer -d cifixer -c "\l"
```

**Missing Environment Variables:**
```bash
# Verify .env file exists and has required values
cat .env | grep -E "(GITHUB_TOKEN|JENKINS_WEBHOOK_SECRET)"

# Restart with updated configuration
docker-compose down
docker-compose up -d
```

**Port Conflicts:**
```bash
# Check if ports are already in use
netstat -tulpn | grep -E "(8080|5432|11434)"

# Change ports in .env file
APP_PORT=8081
POSTGRES_PORT=5433
```

### 2. Database Issues

**Symptoms:**
- "Connection to database failed"
- "Database does not exist"
- Migration errors

**Diagnosis:**
```bash
# Check PostgreSQL logs
docker-compose logs postgres

# Test database connection
docker-compose exec postgres psql -U cifixer -d cifixer -c "SELECT version();"

# Check database tables
docker-compose exec postgres psql -U cifixer -d cifixer -c "\dt"
```

**Solutions:**

**Database Not Ready:**
```bash
# Wait for database to be ready
docker-compose up -d postgres
sleep 30
docker-compose up -d ci-fixer
```

**Permission Issues:**
```bash
# Reset database permissions
docker-compose exec postgres psql -U postgres -c "
  GRANT ALL PRIVILEGES ON DATABASE cifixer TO cifixer;
  GRANT ALL ON SCHEMA public TO cifixer;
"
```

**Corrupted Data:**
```bash
# Reset database (WARNING: This deletes all data)
docker-compose down
docker volume rm ci-fixer_postgres_data
docker-compose up -d
```

### 3. External API Issues

**Symptoms:**
- "External API service unavailable"
- API authentication errors
- Rate limiting errors
- Slow response times

**Diagnosis:**
```bash
# Check API configuration
echo $LLM_API_BASE_URL
echo $LLM_API_MODEL
echo $LLM_API_PROVIDER

# Test API connectivity
curl -H "Authorization: Bearer $LLM_API_KEY" $LLM_API_BASE_URL/models

# Check application logs for API errors
docker-compose logs ci-fixer | grep -i "api\|llm"
```

**Solutions:**

**Authentication Errors:**
```bash
# Verify API key is correct and active
# Check provider dashboard for key status
# Regenerate API key if needed

# Update .env file with new key
LLM_API_KEY=your-new-api-key

# Restart application
docker-compose restart ci-fixer
```

**Rate Limiting:**
```bash
# Check rate limit headers in logs
# Wait for rate limit reset
# Consider upgrading API plan
# Switch to different model with higher limits

# Reduce concurrent requests in application
CIFIXER_MAX_CONCURRENT_TASKS=2
```

**Network Issues:**
```bash
# Test basic connectivity
ping openrouter.ai  # or your provider
curl -I https://openrouter.ai/api/v1/models

# Check firewall rules
# Verify DNS resolution
# Test from different network if possible
```

**Model Not Available:**
```bash
# Check if model exists for your provider
curl -H "Authorization: Bearer $LLM_API_KEY" $LLM_API_BASE_URL/models | grep your-model

# Try alternative model
LLM_API_MODEL=anthropic/claude-3-haiku  # Faster, cheaper alternative
```

### 4. GitHub Integration Issues

**Symptoms:**
- "GitHub API authentication failed"
- "Repository not found"
- PR creation failures

**Diagnosis:**
```bash
# Check GitHub token in logs (should be redacted)
docker-compose logs ci-fixer | grep -i github

# Test GitHub API access
curl -H "Authorization: token YOUR_TOKEN" https://api.github.com/user
```

**Solutions:**

**Invalid Token:**
```bash
# Generate new GitHub token with correct permissions:
# - repo (Full control)
# - workflow (Update workflows)

# Update .env file
GITHUB_TOKEN=ghp_new_token_here

# Restart application
docker-compose restart ci-fixer
```

**Rate Limiting:**
```bash
# Check rate limit status
curl -H "Authorization: token YOUR_TOKEN" https://api.github.com/rate_limit

# Wait for rate limit reset or use different token
```

### 5. Webhook Issues

**Symptoms:**
- Jenkins webhooks not received
- "Invalid signature" errors
- Webhook timeouts

**Diagnosis:**
```bash
# Check webhook logs
docker-compose logs ci-fixer | grep -i webhook

# Test webhook endpoint
curl -X POST http://localhost:8080/webhooks/jenkins \
  -H "Content-Type: application/json" \
  -d '{"test": "payload"}'
```

**Solutions:**

**Signature Validation Failures:**
```bash
# Verify webhook secret matches
echo $JENKINS_WEBHOOK_SECRET

# Temporarily disable signature validation for testing
WEBHOOK_SIGNATURE_VALIDATION=false
docker-compose restart ci-fixer
```

**Network Connectivity:**
```bash
# Check if Jenkins can reach the webhook URL
# From Jenkins server:
curl -v http://your-ci-fixer-host:8080/actuator/health

# Check firewall rules
# Ensure port 8080 is accessible from Jenkins
```

### 6. Email Issues

**Symptoms:**
- Notifications not sent
- SMTP connection errors
- Emails in spam folder

**Diagnosis:**
```bash
# Check MailHog web interface
open http://localhost:8025

# Check email logs
docker-compose logs ci-fixer | grep -i mail

# Test SMTP connection
docker-compose logs mailhog
```

**Solutions:**

**MailHog Not Receiving Emails:**
```bash
# Restart MailHog
docker-compose restart mailhog

# Check MailHog logs
docker-compose logs mailhog
```

**Production SMTP Issues:**
```bash
# Test SMTP credentials
# Use a tool like swaks or telnet to test SMTP connection

# Check SMTP configuration in .env
SPRING_MAIL_HOST=your-smtp-server.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your-email@company.com
SPRING_MAIL_PASSWORD=your-password
```

### 7. Performance Issues

**Symptoms:**
- Slow task processing
- High memory usage
- Container restarts

**Diagnosis:**
```bash
# Check resource usage
docker stats

# Check application metrics
curl http://localhost:8080/actuator/metrics

# Check task queue status
docker-compose logs ci-fixer | grep -i "task\|queue"
```

**Solutions:**

**Memory Issues:**
```bash
# Increase JVM heap size
JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"

# Increase Docker memory limits
# Edit docker-compose.yml deploy.resources.limits.memory
```

**Slow LLM Responses:**
```bash
# Use smaller, faster model
docker-compose exec ollama ollama pull codellama:7b

# Enable GPU acceleration (if available)
# Uncomment GPU configuration in docker-compose.yml
```

**Database Performance:**
```bash
# Check database connections
docker-compose exec postgres psql -U cifixer -d cifixer -c "
  SELECT count(*) FROM pg_stat_activity WHERE state = 'active';
"

# Optimize database settings
# Add to docker-compose.yml postgres service:
# command: postgres -c shared_preload_libraries=pg_stat_statements
```

## Log Analysis

### Important Log Patterns

**Successful Processing:**
```
INFO  - Webhook received for build: job-name #123
INFO  - Plan created with 3 steps for build 456
INFO  - Patch applied successfully to UserService.java
INFO  - Validation passed for build 456
INFO  - PR created: https://github.com/org/repo/pull/789
```

**Error Patterns:**
```
ERROR - Database connection failed
ERROR - GitHub API rate limit exceeded
ERROR - External API service unavailable
ERROR - Invalid webhook signature
ERROR - Patch application failed
```

### Log Aggregation

For production, consider using log aggregation:

```yaml
# Add to docker-compose.yml
services:
  ci-fixer:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

## Recovery Procedures

### Complete System Reset

```bash
# Stop all services
docker-compose down

# Remove all volumes (WARNING: Deletes all data)
docker-compose down -v

# Remove all images
docker-compose down --rmi all

# Start fresh
docker-compose up -d
```

### Partial Recovery

```bash
# Reset only application (keeps database)
docker-compose stop ci-fixer
docker-compose rm -f ci-fixer
docker-compose up -d ci-fixer

# Reset only database (keeps application)
docker-compose stop postgres
docker volume rm ci-fixer_postgres_data
docker-compose up -d postgres
```

## Getting Help

### Collect Diagnostic Information

```bash
#!/bin/bash
# diagnostic-info.sh - Collect system information for support

echo "=== Docker Version ==="
docker --version
docker-compose --version

echo "=== Service Status ==="
docker-compose ps

echo "=== Resource Usage ==="
docker stats --no-stream

echo "=== Recent Logs ==="
docker-compose logs --tail=100 ci-fixer

echo "=== Configuration ==="
docker-compose config

echo "=== Environment ==="
cat .env | sed 's/=.*/=***REDACTED***/'
```

### Support Checklist

Before seeking help:

1. ✅ Check this troubleshooting guide
2. ✅ Verify environment configuration
3. ✅ Check service logs for errors
4. ✅ Test individual components
5. ✅ Collect diagnostic information
6. ✅ Document steps to reproduce the issue

### Contact Information

- GitHub Issues: [Repository Issues](https://github.com/your-org/ci-fixer/issues)
- Documentation: [Project Wiki](https://github.com/your-org/ci-fixer/wiki)
- Email: support@your-company.com