# Operational Runbook

## Quick Reference

### Emergency Contacts
- **Primary On-Call**: [Your Team]
- **Secondary On-Call**: [Backup Team]
- **Escalation**: [Management]

### Key URLs
- **Application**: http://localhost:8080/api
- **Health Check**: http://localhost:8080/actuator/health
- **Admin Dashboard**: http://localhost:8080/admin/status
- **Metrics**: http://localhost:8080/actuator/prometheus
- **Grafana**: http://localhost:3000 (if configured)

## Service Overview

The Multi-Agent CI Fixer is a Spring Boot application that:
1. Receives Jenkins build failure webhooks
2. Analyzes build logs and generates fixes using LLM APIs
3. Creates GitHub pull requests with automated fixes
4. Sends notifications to stakeholders

### Architecture Components
- **Web Layer**: REST API for webhooks and admin operations
- **Orchestrator**: Task queue processor and coordinator
- **Agents**: Specialized processors (Planner, Retriever, CodeFix, Validator, PR, Notification)
- **Database**: PostgreSQL for persistence
- **External APIs**: GitHub, LLM providers (OpenRouter, OpenAI, etc.)

## Standard Operating Procedures

### Daily Health Check (5 minutes)

1. **Check Application Health**:
```bash
curl -f http://localhost:8080/actuator/health || echo "ALERT: Health check failed"
```

2. **Verify Task Processing**:
```bash
curl -s http://localhost:8080/admin/status | jq '.tasks'
```
Expected: Pending tasks < 50, no stuck processing tasks

3. **Check Database Connections**:
```bash
curl -s http://localhost:8080/admin/status | jq '.database'
```
Expected: Active connections < 80% of max pool size

4. **Review Recent Errors**:
```bash
tail -100 logs/cifixer.log | grep -i error
```

### Weekly Maintenance (30 minutes)

1. **Review Performance Metrics**:
   - Task processing times (should be < 5 minutes average)
   - Build success rate (should be > 70%)
   - External API latency (should be < 30 seconds)

2. **Database Maintenance**:
```bash
# Check database size
psql -h localhost -U cifixer -c "SELECT pg_size_pretty(pg_database_size('cifixer'));"

# Check for long-running queries
psql -h localhost -U cifixer -c "SELECT pid, now() - pg_stat_activity.query_start AS duration, query FROM pg_stat_activity WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';"
```

3. **Log Rotation Check**:
```bash
ls -lah logs/
# Ensure logs are rotating and not consuming excessive disk space
```

4. **Clean Old Working Directories**:
```bash
find /work -type d -mtime +7 -exec rm -rf {} \; 2>/dev/null
```

## Incident Response Procedures

### Severity Levels

#### P1 - Critical (Response: Immediate)
- Application completely down
- Database unavailable
- All task processing stopped

#### P2 - High (Response: 15 minutes)
- High error rate (>50% task failures)
- External API completely unavailable
- Memory/disk space critical

#### P3 - Medium (Response: 1 hour)
- Moderate error rate (20-50% task failures)
- Performance degradation
- Individual component failures

#### P4 - Low (Response: Next business day)
- Minor errors
- Non-critical feature issues
- Monitoring alerts

### P1 Incident Response

1. **Immediate Assessment** (2 minutes):
```bash
# Check if application is responding
curl -f http://localhost:8080/actuator/health

# Check if database is accessible
psql -h localhost -U cifixer -c "SELECT 1;"

# Check system resources
df -h
free -m
```

2. **Quick Recovery Actions** (5 minutes):
```bash
# Restart application if needed
docker-compose restart cifixer

# Check logs for immediate errors
docker-compose logs --tail=50 cifixer
```

3. **Escalation** (if not resolved in 10 minutes):
   - Contact secondary on-call
   - Create incident ticket
   - Begin detailed investigation

### P2 Incident Response

1. **Identify Root Cause** (15 minutes):
```bash
# Check recent failed tasks
curl -s "http://localhost:8080/admin/tasks?status=FAILED&size=20" | jq '.'

# Review error patterns
grep -A5 -B5 "ERROR" logs/cifixer.log | tail -50

# Check external API status
curl -s http://localhost:8080/admin/health | jq '.database, .taskQueue'
```

2. **Mitigation Actions**:
   - Retry failed tasks if appropriate
   - Adjust rate limits for external APIs
   - Scale resources if needed

### Common Issues and Solutions

#### Issue: High Task Failure Rate

**Symptoms**:
- Many tasks in FAILED status
- Error logs showing repeated failures
- Build processing not completing

**Investigation**:
```bash
# Check recent failures
curl -s "http://localhost:8080/admin/tasks?status=FAILED&size=10" | jq '.content[].errorMessage'

# Check external API connectivity
curl -s http://localhost:8080/actuator/health | jq '.components'
```

**Solutions**:
1. **LLM API Issues**:
```bash
# Check API key and endpoint
curl -H "Authorization: Bearer $LLM_API_KEY" $LLM_API_BASE_URL/models
```

2. **GitHub API Issues**:
```bash
# Check GitHub API rate limits
curl -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/rate_limit
```

3. **Retry Failed Tasks**:
```bash
# Retry specific build
curl -X POST http://localhost:8080/admin/builds/{buildId}/retry
```

#### Issue: Database Connection Pool Exhausted

**Symptoms**:
- "Connection timeout" errors
- High number of threads awaiting connections
- Application becomes unresponsive

**Investigation**:
```bash
# Check connection pool status
curl -s http://localhost:8080/admin/status | jq '.database'

# Check for connection leaks
grep "HikariPool" logs/cifixer.log | tail -20
```

**Solutions**:
1. **Immediate**: Restart application to reset connection pool
2. **Short-term**: Increase pool size in configuration
3. **Long-term**: Investigate connection leaks in code

#### Issue: Memory Issues

**Symptoms**:
- OutOfMemoryError in logs
- Application becomes slow
- GC pressure

**Investigation**:
```bash
# Check JVM memory usage
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq '.'

# Check heap usage
curl -s http://localhost:8080/actuator/metrics/jvm.memory.max | jq '.'
```

**Solutions**:
1. **Immediate**: Restart application
2. **Increase heap size**: Add `-Xmx4g` to JVM options
3. **Investigate memory leaks**: Use profiling tools

#### Issue: Task Queue Backlog

**Symptoms**:
- Large number of PENDING tasks
- Tasks not being processed
- Increasing queue size

**Investigation**:
```bash
# Check queue statistics
curl -s http://localhost:8080/admin/queue/stats | jq '.'

# Check orchestrator status
grep "Orchestrator" logs/cifixer.log | tail -10
```

**Solutions**:
1. **Check orchestrator**: Ensure it's running and processing tasks
2. **Increase concurrency**: Adjust `orchestrator.max.concurrent.tasks`
3. **Clear stuck tasks**: Manually retry or reset stuck tasks

## Monitoring and Alerting

### Key Metrics to Monitor

1. **Application Health**:
   - HTTP response codes
   - Response times
   - Error rates

2. **Task Processing**:
   - Task completion rate
   - Task processing duration
   - Queue size

3. **External Dependencies**:
   - LLM API response times
   - GitHub API response times
   - Database connection health

4. **System Resources**:
   - CPU usage
   - Memory usage
   - Disk space

### Alert Thresholds

```yaml
# Recommended alert thresholds
alerts:
  task_failure_rate: > 20% over 5 minutes
  task_queue_size: > 100 pending tasks
  response_time: > 30 seconds (95th percentile)
  database_connections: > 80% of pool size
  memory_usage: > 85% of heap
  disk_space: > 90% used
  external_api_latency: > 60 seconds
```

### Grafana Dashboard Queries

```promql
# Task processing rate
rate(cifixer_task_success_total[5m])

# Task failure rate
rate(cifixer_task_failure_total[5m])

# Average task processing time
rate(cifixer_task_processing_duration_sum[5m]) / rate(cifixer_task_processing_duration_count[5m])

# Database connection usage
cifixer_database_connections_active / cifixer_database_connections_total * 100

# Queue size
cifixer_queue_size
```

## Backup and Recovery

### Database Backup

**Daily Backup Script**:
```bash
#!/bin/bash
BACKUP_DIR="/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/cifixer_backup_$DATE.sql"

pg_dump -h localhost -U cifixer cifixer > $BACKUP_FILE
gzip $BACKUP_FILE

# Keep only last 30 days
find $BACKUP_DIR -name "cifixer_backup_*.sql.gz" -mtime +30 -delete
```

**Recovery Process**:
```bash
# Stop application
docker-compose stop cifixer

# Restore database
gunzip -c backup_file.sql.gz | psql -h localhost -U cifixer cifixer

# Start application
docker-compose start cifixer

# Verify recovery
curl -f http://localhost:8080/actuator/health
```

### Configuration Backup

**Backup Configuration**:
```bash
# Backup configuration files
tar -czf config_backup_$(date +%Y%m%d).tar.gz \
  docker-compose.yml \
  .env \
  src/main/resources/application.yml
```

## Security Procedures

### Token Rotation

**GitHub Token**:
1. Generate new token in GitHub settings
2. Update environment variable: `GITHUB_TOKEN`
3. Restart application
4. Verify GitHub API connectivity

**LLM API Key**:
1. Generate new API key from provider
2. Update environment variable: `LLM_API_KEY`
3. Restart application
4. Verify LLM API connectivity

### Security Incident Response

1. **Immediate Actions**:
   - Rotate compromised tokens
   - Review access logs
   - Check for unauthorized changes

2. **Investigation**:
   - Analyze application logs
   - Check database for unauthorized access
   - Review recent configuration changes

3. **Recovery**:
   - Update all credentials
   - Patch security vulnerabilities
   - Implement additional monitoring

## Capacity Planning

### Scaling Indicators

**Scale Up When**:
- CPU usage > 70% sustained
- Memory usage > 80% sustained
- Task queue size > 200 sustained
- Database connections > 80% of pool

**Scale Out When**:
- Single instance cannot handle load
- Need geographic distribution
- Require high availability

### Performance Baselines

- **Task Processing**: 2-5 minutes average
- **Build Processing**: 10-30 minutes end-to-end
- **API Response Time**: < 5 seconds (95th percentile)
- **Database Query Time**: < 1 second average
- **Memory Usage**: < 70% of allocated heap

## Deployment Procedures

### Rolling Deployment

1. **Pre-deployment Checks**:
```bash
# Verify current health
curl -f http://localhost:8080/actuator/health

# Check pending tasks
curl -s http://localhost:8080/admin/status | jq '.tasks.pending'
```

2. **Deployment Steps**:
```bash
# Pull new image
docker-compose pull cifixer

# Stop application gracefully
docker-compose stop cifixer

# Start with new version
docker-compose up -d cifixer

# Wait for startup
sleep 30

# Verify deployment
curl -f http://localhost:8080/actuator/health
```

3. **Post-deployment Verification**:
```bash
# Check application version
curl -s http://localhost:8080/actuator/info

# Verify task processing
curl -s http://localhost:8080/admin/status

# Monitor logs for errors
docker-compose logs --tail=50 cifixer
```

### Rollback Procedure

```bash
# Stop current version
docker-compose stop cifixer

# Revert to previous image
docker tag cifixer:previous cifixer:latest

# Start previous version
docker-compose up -d cifixer

# Verify rollback
curl -f http://localhost:8080/actuator/health
```

## Contact Information

### Escalation Matrix

1. **Level 1**: On-call Engineer
2. **Level 2**: Team Lead
3. **Level 3**: Engineering Manager
4. **Level 4**: Director of Engineering

### External Dependencies

- **GitHub Support**: [GitHub Support Portal]
- **LLM Provider Support**: [Provider-specific support]
- **Infrastructure Team**: [Internal contact]
- **Database Team**: [Internal contact]

## Documentation Links

- [Application Documentation](README.md)
- [Monitoring Guide](MONITORING.md)
- [Deployment Guide](DEPLOYMENT.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [API Documentation](API.md)