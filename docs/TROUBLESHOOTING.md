# Troubleshooting Guide

## Overview

This guide covers common issues you may encounter when deploying and operating the Multi-Agent CI Fixer, along with their solutions.

## Common Deployment Issues

### 1. Application Won't Start

#### Symptoms
- Container exits immediately
- Health check fails
- No response on port 8080

#### Diagnosis
```bash
# Check container logs
docker-compose logs app

# Check container status
docker-compose ps

# Check port binding
netstat -tlnp | grep 8080
```

#### Common Causes and Solutions

**Database Connection Failed**
```
Error: Could not connect to PostgreSQL database
```

Solution:
```bash
# Check database container
docker-compose logs db

# Verify database is running
docker-compose exec db pg_isready -U cifixer -d cifixer

# Check environment variables
docker-compose exec app env | grep SPRING_DATASOURCE
```

**Missing Environment Variables**
```
Error: Required environment variable not set
```

Solution:
```bash
# Check .env file exists and is properly formatted
cat .env

# Verify environment variables are loaded
docker-compose config
```

**Port Already in Use**
```
Error: Port 8080 is already in use
```

Solution:
```bash
# Find process using port 8080
sudo lsof -i :8080

# Kill the process or change port in docker-compose.yml
```

### 2. Database Issues

#### PostgreSQL Won't Start

**Symptoms:**
- Database container exits with error
- Connection refused errors

**Diagnosis:**
```bash
# Check database logs
docker-compose logs db

# Check data directory permissions
ls -la ./data/postgres/

# Check disk space
df -h
```

**Solutions:**

**Permission Issues:**
```bash
# Fix data directory permissions
sudo chown -R 999:999 ./data/postgres/
```

**Disk Space Full:**
```bash
# Clean up old logs and data
docker system prune -f
docker volume prune -f
```

**Corrupted Data:**
```bash
# Backup and recreate database
docker-compose down
docker volume rm multi-agent-ci-fixer_postgres_data
docker-compose up -d db
```

#### Database Migration Failures

**Symptoms:**
- Application starts but database schema is incorrect
- Flyway migration errors

**Diagnosis:**
```bash
# Check migration status
docker-compose exec db psql -U cifixer -d cifixer -c "SELECT * FROM flyway_schema_history;"

# Check table structure
docker-compose exec db psql -U cifixer -d cifixer -c "\dt"
```

**Solutions:**

**Failed Migration:**
```bash
# Reset database and rerun migrations
docker-compose down
docker volume rm multi-agent-ci-fixer_postgres_data
docker-compose up -d
```

**Manual Migration Fix:**
```sql
-- Connect to database
docker-compose exec db psql -U cifixer -d cifixer

-- Check failed migration
SELECT * FROM flyway_schema_history WHERE success = false;

-- Fix manually and mark as successful
UPDATE flyway_schema_history SET success = true WHERE version = 'X.X';
```

### 3. External API Integration Issues

#### API Authentication Failures

**Symptoms:**
- 401 Unauthorized errors in logs
- Tasks stuck in FAILED status with API errors

**Diagnosis:**
```bash
# Check API configuration
docker-compose exec app env | grep LLM_API

# Test API connectivity
curl -H "Authorization: Bearer $LLM_API_KEY" $LLM_API_BASE_URL/models
```

**Solutions:**

**Invalid API Key:**
```bash
# Verify API key format and permissions
# OpenAI: sk-...
# OpenRouter: sk-or-...
# Anthropic: sk-ant-...

# Update environment variable
docker-compose down
# Edit .env file
docker-compose up -d
```

**Wrong API Endpoint:**
```bash
# Verify correct endpoints:
# OpenAI: https://api.openai.com/v1
# OpenRouter: https://openrouter.ai/api/v1
# Anthropic: https://api.anthropic.com/v1
```

#### Rate Limiting Issues

**Symptoms:**
- 429 Too Many Requests errors
- Slow task processing

**Solutions:**
```yaml
# Add rate limiting configuration to application.yml
llm:
  api:
    rate-limit:
      requests-per-minute: 60
      retry-delay-seconds: 60
```

#### API Response Format Issues

**Symptoms:**
- Invalid response format errors
- Patch application failures

**Diagnosis:**
```bash
# Check recent API responses in logs
docker-compose logs app | grep "LLM Response"

# Enable debug logging for API client
docker-compose exec app curl -X POST localhost:8080/actuator/loggers/com.example.cifixer.llm -H "Content-Type: application/json" -d '{"configuredLevel":"DEBUG"}'
```

### 4. Git Operations Issues

#### Repository Access Problems

**Symptoms:**
- Clone/pull operations fail
- Authentication errors with Git repositories

**Diagnosis:**
```bash
# Check Git configuration
docker-compose exec app env | grep GIT

# Test Git access manually
docker-compose exec app git clone $REPO_URL /tmp/test-clone
```

**Solutions:**

**SSH Key Issues:**
```bash
# Add SSH key to container
docker-compose exec app ssh-keygen -t rsa -b 4096
# Add public key to GitHub/GitLab

# Or use HTTPS with token
GIT_TOKEN=your_token_here
```

**Repository Not Found:**
```bash
# Verify repository URL format
# HTTPS: https://github.com/owner/repo.git
# SSH: git@github.com:owner/repo.git
```

#### Working Directory Issues

**Symptoms:**
- Disk space errors
- Permission denied errors

**Solutions:**
```bash
# Clean up old working directories
docker-compose exec app find /app/work -type d -mtime +7 -exec rm -rf {} +

# Fix permissions
docker-compose exec app chown -R app:app /app/work
```

### 5. Webhook Issues

#### Webhooks Not Received

**Symptoms:**
- No new builds appear in database
- Jenkins/CI system shows webhook errors

**Diagnosis:**
```bash
# Check webhook endpoint
curl -X POST http://localhost:8080/webhooks/jenkins

# Check network connectivity from CI system
# From Jenkins server:
curl -v http://ci-fixer-host:8080/actuator/health
```

**Solutions:**

**Network Connectivity:**
```bash
# Check firewall rules
sudo ufw status
sudo iptables -L

# Check Docker network
docker network ls
docker network inspect multi-agent-ci-fixer_default
```

**Webhook Signature Validation:**
```bash
# Disable signature validation for testing
WEBHOOK_SIGNATURE_VALIDATION_ENABLED=false

# Or verify signature generation in CI system
```

#### Invalid Webhook Payload

**Symptoms:**
- 400 Bad Request responses
- Validation errors in logs

**Diagnosis:**
```bash
# Check webhook payload format
docker-compose logs app | grep "Webhook validation"

# Test with valid payload
curl -X POST http://localhost:8080/webhooks/jenkins \
  -H "Content-Type: application/json" \
  -d '{"job":"test","buildNumber":1,"branch":"main","repoUrl":"https://github.com/test/repo.git","commitSha":"abc123","logs":"dGVzdA==","status":"FAILURE","timestamp":"2024-01-01T00:00:00Z"}'
```

## Performance Issues

### 1. Slow Task Processing

#### Symptoms
- Tasks remain in PENDING status for long periods
- High CPU/memory usage

#### Diagnosis
```bash
# Check task queue status
docker-compose exec db psql -U cifixer -d cifixer -c "SELECT type, status, COUNT(*) FROM tasks GROUP BY type, status;"

# Check system resources
docker stats

# Check application metrics
curl http://localhost:8080/actuator/metrics/cifixer.tasks.duration
```

#### Solutions

**Increase Concurrency:**
```yaml
# In application.yml
task:
  processing:
    thread-pool-size: 10
    max-queue-size: 100
```

**Optimize Database:**
```sql
-- Add missing indexes
CREATE INDEX CONCURRENTLY idx_tasks_status_created ON tasks(status, created_at);
CREATE INDEX CONCURRENTLY idx_builds_status_updated ON builds(status, updated_at);

-- Analyze query performance
EXPLAIN ANALYZE SELECT * FROM tasks WHERE status = 'PENDING' ORDER BY created_at LIMIT 10;
```

### 2. Memory Issues

#### Symptoms
- OutOfMemoryError in logs
- Container restarts frequently

#### Solutions
```yaml
# Increase container memory limit
services:
  app:
    deploy:
      resources:
        limits:
          memory: 2G
```

```yaml
# Optimize JVM settings
services:
  app:
    environment:
      - JAVA_OPTS=-Xmx1536m -Xms512m -XX:+UseG1GC
```

### 3. Disk Space Issues

#### Symptoms
- No space left on device errors
- Application crashes

#### Solutions
```bash
# Clean up working directories
find /opt/ci-fixer/work -type d -mtime +7 -exec rm -rf {} +

# Clean up Docker resources
docker system prune -f
docker volume prune -f

# Set up log rotation
cat > /etc/logrotate.d/ci-fixer << 'EOF'
/opt/ci-fixer/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 644 root root
}
EOF
```

## Monitoring and Debugging

### 1. Enable Debug Logging

```yaml
# In application.yml or environment variables
logging:
  level:
    com.example.cifixer: DEBUG
    org.springframework.web: DEBUG
    org.springframework.security: DEBUG
```

### 2. Health Check Endpoints

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Detailed health information
curl http://localhost:8080/actuator/health/db
curl http://localhost:8080/actuator/health/diskSpace

# Application info
curl http://localhost:8080/actuator/info

# Metrics
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/cifixer.tasks.total
```

### 3. Database Debugging

```sql
-- Connect to database
docker-compose exec db psql -U cifixer -d cifixer

-- Check recent builds
SELECT id, job, build_number, status, created_at 
FROM builds 
ORDER BY created_at DESC 
LIMIT 10;

-- Check task status distribution
SELECT type, status, COUNT(*) as count, 
       AVG(EXTRACT(EPOCH FROM (updated_at - created_at))) as avg_duration_seconds
FROM tasks 
GROUP BY type, status;

-- Check failed tasks
SELECT t.id, t.type, t.status, t.error_message, b.job, b.build_number
FROM tasks t
JOIN builds b ON t.build_id = b.id
WHERE t.status = 'FAILED'
ORDER BY t.updated_at DESC
LIMIT 10;

-- Check recent patches
SELECT p.id, p.file_path, p.applied, b.job, b.build_number
FROM patches p
JOIN builds b ON p.build_id = b.id
ORDER BY p.created_at DESC
LIMIT 10;
```

### 4. Log Analysis

```bash
# Follow application logs
docker-compose logs -f app

# Search for specific errors
docker-compose logs app | grep -i error

# Check webhook processing
docker-compose logs app | grep "Webhook received"

# Check task processing
docker-compose logs app | grep "Processing task"

# Check API calls
docker-compose logs app | grep "LLM API"
```

## Recovery Procedures

### 1. Restart Failed Tasks

```sql
-- Reset failed tasks for retry
UPDATE tasks 
SET status = 'PENDING', attempt = 0, error_message = NULL 
WHERE status = 'FAILED' AND attempt < max_attempts;
```

### 2. Clean Up Stuck Builds

```sql
-- Find builds stuck in processing
SELECT id, job, build_number, status, created_at
FROM builds 
WHERE status = 'PROCESSING' 
AND created_at < NOW() - INTERVAL '2 hours';

-- Reset stuck builds
UPDATE builds 
SET status = 'FAILED' 
WHERE status = 'PROCESSING' 
AND created_at < NOW() - INTERVAL '2 hours';
```

### 3. Emergency Shutdown

```bash
# Graceful shutdown
docker-compose down

# Force shutdown if needed
docker-compose kill
docker-compose rm -f

# Clean up resources
docker system prune -f
```

### 4. Backup and Restore

#### Create Backup
```bash
# Database backup
docker-compose exec -T db pg_dump -U cifixer cifixer | gzip > backup-$(date +%Y%m%d).sql.gz

# Configuration backup
tar -czf config-backup-$(date +%Y%m%d).tar.gz .env docker-compose*.yml
```

#### Restore from Backup
```bash
# Stop application
docker-compose down

# Restore database
gunzip -c backup-20240115.sql.gz | docker-compose exec -T db psql -U cifixer -d cifixer

# Start application
docker-compose up -d
```

## Getting Help

### 1. Collect Diagnostic Information

Before seeking help, collect the following information:

```bash
#!/bin/bash
# diagnostic-info.sh

echo "=== System Information ==="
uname -a
docker --version
docker-compose --version

echo "=== Container Status ==="
docker-compose ps

echo "=== Container Logs (last 100 lines) ==="
docker-compose logs --tail=100 app

echo "=== Database Status ==="
docker-compose exec db pg_isready -U cifixer -d cifixer

echo "=== Recent Builds ==="
docker-compose exec db psql -U cifixer -d cifixer -c "SELECT id, job, status, created_at FROM builds ORDER BY created_at DESC LIMIT 5;"

echo "=== Task Queue Status ==="
docker-compose exec db psql -U cifixer -d cifixer -c "SELECT type, status, COUNT(*) FROM tasks GROUP BY type, status;"

echo "=== Disk Usage ==="
df -h

echo "=== Memory Usage ==="
free -h

echo "=== Network Connectivity ==="
curl -s http://localhost:8080/actuator/health || echo "Health check failed"
```

### 2. Log Levels for Support

Enable detailed logging for support:

```yaml
logging:
  level:
    com.example.cifixer: DEBUG
    org.springframework.web: DEBUG
    org.springframework.security: DEBUG
    org.springframework.transaction: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 3. Common Log Patterns

**Successful Processing:**
```
INFO  - Webhook received for job: test-project, build: 123
INFO  - Created build with ID: 456
INFO  - Enqueued PLAN task for build: 456
INFO  - Processing PLAN task for build: 456
INFO  - Created plan with 3 steps for build: 456
INFO  - Enqueued RETRIEVE task for build: 456
...
INFO  - PR created successfully: https://github.com/org/repo/pull/789
```

**Failed Processing:**
```
ERROR - Failed to process PATCH task for build: 456
ERROR - API call failed: 401 Unauthorized
ERROR - Retrying task in 4 seconds (attempt 2/3)
```

### 4. Support Channels

When reporting issues, include:
- Diagnostic information from script above
- Relevant log excerpts
- Steps to reproduce the issue
- Expected vs actual behavior
- Environment details (dev/staging/prod)

For more information, see:
- [Deployment Guide](DEPLOYMENT.md)
- [Webhook Configuration](WEBHOOK_CONFIGURATION.md)
- [Monitoring Guide](MONITORING.md)
- [Security Best Practices](SECURITY.md)