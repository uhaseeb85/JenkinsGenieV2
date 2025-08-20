# Monitoring and Operations Guide

## Overview

The Multi-Agent CI Fixer includes comprehensive monitoring and operational features to ensure reliable operation and easy troubleshooting. This guide covers metrics, logging, health checks, and administrative operations.

## Metrics and Monitoring

### Custom Metrics

The application exposes custom metrics via Micrometer and Prometheus:

#### Task Processing Metrics
- `cifixer.task.processing.duration` - Time taken to process tasks (by task type)
- `cifixer.task.success` - Number of successfully processed tasks (by task type)
- `cifixer.task.failure` - Number of failed tasks (by task type and error type)
- `cifixer.task.retry` - Number of task retries (by task type and attempt number)

#### Agent-Specific Metrics
- `cifixer.agent.planner.duration` - Time taken by planner agent
- `cifixer.agent.retriever.duration` - Time taken by retriever agent
- `cifixer.agent.codefix.duration` - Time taken by code-fix agent
- `cifixer.agent.validator.duration` - Time taken by validator agent
- `cifixer.agent.pr.duration` - Time taken by PR agent
- `cifixer.agent.notification.duration` - Time taken by notification agent

#### Build Processing Metrics
- `cifixer.build.processed` - Number of builds processed (by job and branch)
- `cifixer.build.success` - Number of successfully processed builds
- `cifixer.build.failure` - Number of failed builds
- `cifixer.build.processing.duration` - Total time to process a build

#### External API Metrics
- `cifixer.external.llm.duration` - Time taken for LLM API calls
- `cifixer.external.llm.success` - Number of successful LLM API calls
- `cifixer.external.llm.failure` - Number of failed LLM API calls
- `cifixer.external.github.duration` - Time taken for GitHub API calls
- `cifixer.external.github.success` - Number of successful GitHub API calls
- `cifixer.external.github.failure` - Number of failed GitHub API calls

#### Database Metrics
- `cifixer.database.connections.active` - Number of active database connections
- `cifixer.database.connections.idle` - Number of idle database connections
- `cifixer.database.connections.total` - Total number of database connections
- `cifixer.database.connections.pending` - Number of threads awaiting connections
- `cifixer.database.connections.timeout.count` - Total connection timeouts
- `cifixer.database.connections.creation.count` - Total connections created

### Accessing Metrics

#### Prometheus Endpoint
```
GET /actuator/prometheus
```

#### Metrics Endpoint (JSON)
```
GET /actuator/metrics
GET /actuator/metrics/{metric-name}
```

### Setting Up Prometheus

1. **Prometheus Configuration** (`prometheus.yml`):
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'cifixer'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

2. **Docker Compose for Monitoring Stack**:
```yaml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-storage:/var/lib/grafana

volumes:
  grafana-storage:
```

## Structured Logging

### Log Format

The application uses structured JSON logging with correlation IDs:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "message": "Task completed successfully",
  "service": "multi-agent-ci-fixer",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "buildId": "12345",
  "taskId": "67890",
  "context": {
    "event": "task_completed",
    "taskType": "PLAN",
    "durationMs": 1500
  }
}
```

### Key Log Events

#### Task Processing Events
- `task_started` - When a task begins processing
- `task_completed` - When a task completes successfully
- `task_failed` - When a task fails
- `task_retry` - When a task is scheduled for retry

#### Build Processing Events
- `build_processing_started` - When build processing begins
- `build_processing_completed` - When build processing finishes

#### External API Events
- `external_api_call` - When an external API call is initiated
- `external_api_response` - When an external API call completes

### Log Configuration

Environment variables for log configuration:
```bash
LOG_FILE_PATH=logs/cifixer.log
LOG_FILE_MAX_SIZE=100MB
LOG_FILE_MAX_HISTORY=30
LOG_FILE_TOTAL_SIZE_CAP=1GB
MONITORING_STRUCTURED_LOGGING_ENABLED=true
MONITORING_CORRELATION_ID_ENABLED=true
```

## Health Checks

### Standard Health Endpoint
```
GET /actuator/health
```

### Detailed Health Endpoint
```
GET /admin/health
```

Returns detailed system health including:
- Database connection pool status
- Task queue health
- Overall system status

### Health Check Components

1. **Database Health**: Monitors HikariCP connection pool
2. **Task Queue Health**: Monitors pending and processing task counts
3. **Custom Health Indicators**: Application-specific health checks

## Administrative Endpoints

### System Status
```
GET /admin/status
```

Returns comprehensive system overview:
- Task queue statistics
- Build statistics  
- Database connection pool status
- System memory and CPU information

### Task Management

#### List Tasks
```
GET /admin/tasks?page=0&size=20&status=FAILED
```

#### Get Task Details
```
GET /admin/tasks/{taskId}
```

#### Retry Failed Task
```
POST /admin/tasks/{taskId}/retry
```

### Build Management

#### List Builds
```
GET /admin/builds?page=0&size=20&status=FAILED
```

#### Get Build Details
```
GET /admin/builds/{buildId}
```

#### Get Build Tasks
```
GET /admin/builds/{buildId}/tasks
```

#### Retry All Failed Tasks for Build
```
POST /admin/builds/{buildId}/retry
```

### Queue Statistics
```
GET /admin/queue/stats
```

Returns:
- Tasks by type and status
- Recent task processing times
- Performance metrics

## Operational Procedures

### Daily Operations

1. **Check System Health**:
```bash
curl http://localhost:8080/admin/health
```

2. **Monitor Queue Status**:
```bash
curl http://localhost:8080/admin/status
```

3. **Review Failed Tasks**:
```bash
curl "http://localhost:8080/admin/tasks?status=FAILED"
```

### Weekly Operations

1. **Review Metrics Trends**:
   - Check Grafana dashboards
   - Review task processing times
   - Monitor error rates

2. **Database Maintenance**:
   - Check connection pool metrics
   - Review slow query logs
   - Monitor disk usage

### Troubleshooting

#### High Task Failure Rate

1. Check recent failed tasks:
```bash
curl "http://localhost:8080/admin/tasks?status=FAILED&size=50"
```

2. Review error patterns in logs:
```bash
grep "task_failed" logs/cifixer.log | tail -20
```

3. Check external API health:
```bash
curl http://localhost:8080/actuator/health
```

#### Database Connection Issues

1. Check connection pool metrics:
```bash
curl http://localhost:8080/actuator/metrics/cifixer.database.connections.active
```

2. Review database health:
```bash
curl http://localhost:8080/admin/health
```

3. Check for connection leaks:
```bash
grep "HikariPool" logs/cifixer.log
```

#### Memory Issues

1. Check JVM metrics:
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

2. Review garbage collection:
```bash
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
```

3. Monitor heap usage trends in Grafana

### Alerting Rules

#### Prometheus Alerting Rules (`alerts.yml`):

```yaml
groups:
  - name: cifixer
    rules:
      - alert: HighTaskFailureRate
        expr: rate(cifixer_task_failure_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High task failure rate detected"
          description: "Task failure rate is {{ $value }} failures per second"

      - alert: DatabaseConnectionPoolExhausted
        expr: cifixer_database_connections_active >= cifixer_database_connections_total * 0.9
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool nearly exhausted"
          description: "{{ $value }} of maximum connections in use"

      - alert: TaskQueueBacklog
        expr: cifixer_queue_size{queue_type="pending"} > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Large task queue backlog"
          description: "{{ $value }} pending tasks in queue"

      - alert: ExternalAPIHighLatency
        expr: histogram_quantile(0.95, rate(cifixer_external_llm_duration_bucket[5m])) > 30
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "High LLM API latency"
          description: "95th percentile latency is {{ $value }} seconds"
```

### Performance Tuning

#### Database Connection Pool

Adjust HikariCP settings based on load:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

#### Task Processing

Adjust concurrent task processing:
```yaml
orchestrator:
  max:
    concurrent:
      tasks: 10
```

#### JVM Tuning

For production deployment:
```bash
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

## Backup and Recovery

### Database Backup

Daily automated backup:
```bash
#!/bin/bash
pg_dump -h localhost -U cifixer cifixer > backup_$(date +%Y%m%d).sql
```

### Log Rotation

Configure logrotate for application logs:
```
/path/to/logs/cifixer.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 644 app app
}
```

### Disaster Recovery

1. **Database Recovery**:
```bash
psql -h localhost -U cifixer cifixer < backup_20240115.sql
```

2. **Application Recovery**:
   - Restore configuration files
   - Restart application
   - Verify health endpoints
   - Check task processing resumption

## Security Considerations

### Endpoint Security

Admin endpoints should be secured:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

### Log Security

- Ensure sensitive data is not logged
- Use structured logging to avoid log injection
- Implement log aggregation with proper access controls

### Metrics Security

- Secure Prometheus endpoint access
- Use authentication for Grafana
- Implement network-level security for monitoring stack