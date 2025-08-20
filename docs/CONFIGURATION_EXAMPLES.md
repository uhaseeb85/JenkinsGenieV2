# Configuration Examples

## Overview

This document provides complete configuration examples for deploying the Multi-Agent CI Fixer in different environments (development, staging, production) with various external API providers.

## Environment Configuration Files

### Development Environment

#### .env.dev
```bash
# Development Environment Configuration

# Database Configuration
POSTGRES_DB=cifixer_dev
POSTGRES_USER=cifixer_dev
POSTGRES_PASSWORD=dev_password_123

# Application Configuration
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
LOGGING_LEVEL_ROOT=DEBUG
LOGGING_LEVEL_COM_EXAMPLE_CIFIXER=TRACE

# GitHub Integration (use test repository)
GITHUB_TOKEN=ghp_dev_token_here
GITHUB_BASE_URL=https://api.github.com

# External API Configuration - OpenRouter (cost-effective for dev)
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=sk-or-dev_key_here
LLM_API_MODEL=anthropic/claude-3-haiku

# Webhook Security (use simple secret for dev)
WEBHOOK_SECRET=dev_webhook_secret_123
WEBHOOK_SIGNATURE_VALIDATION_ENABLED=false

# Email Configuration (use MailHog for testing)
SPRING_MAIL_HOST=mailhog
SPRING_MAIL_PORT=1025
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
NOTIFICATION_FROM_EMAIL=ci-fixer-dev@localhost
NOTIFICATION_TO_EMAILS=dev@localhost

# Working Directory
WORK_DIR=/app/work

# Development-specific settings
CLEANUP_ENABLED=false
TASK_RETRY_ENABLED=true
MAX_CONCURRENT_TASKS=2
```

#### docker-compose.dev.yml
```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
      target: development
    ports:
      - "8080:8080"
      - "5005:5005"  # Debug port
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - LLM_API_BASE_URL=${LLM_API_BASE_URL}
      - LLM_API_KEY=${LLM_API_KEY}
      - LLM_API_MODEL=${LLM_API_MODEL}
      - WEBHOOK_SECRET=${WEBHOOK_SECRET}
      - WEBHOOK_SIGNATURE_VALIDATION_ENABLED=${WEBHOOK_SIGNATURE_VALIDATION_ENABLED}
      - SPRING_MAIL_HOST=${SPRING_MAIL_HOST}
      - SPRING_MAIL_PORT=${SPRING_MAIL_PORT}
      - NOTIFICATION_FROM_EMAIL=${NOTIFICATION_FROM_EMAIL}
      - NOTIFICATION_TO_EMAILS=${NOTIFICATION_TO_EMAILS}
      - WORK_DIR=${WORK_DIR}
      - JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Xmx1g
    volumes:
      - ./work:/app/work
      - ./logs:/app/logs
      - .:/app/src  # Mount source for hot reload
    depends_on:
      - db
      - mailhog
    restart: "no"  # Don't restart in dev

  db:
    image: postgres:14
    environment:
      - POSTGRES_DB=${POSTGRES_DB}
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"  # Expose for external access
    volumes:
      - postgres_dev_data:/var/lib/postgresql/data
      - ./docker/init-scripts:/docker-entrypoint-initdb.d
    restart: unless-stopped

  mailhog:
    image: mailhog/mailhog:latest
    ports:
      - "1025:1025"  # SMTP
      - "8025:8025"  # Web UI
    restart: unless-stopped

  # Development tools
  pgadmin:
    image: dpage/pgadmin4:latest
    environment:
      - PGADMIN_DEFAULT_EMAIL=admin@dev.local
      - PGADMIN_DEFAULT_PASSWORD=admin
    ports:
      - "8081:80"
    depends_on:
      - db

volumes:
  postgres_dev_data:

networks:
  default:
    name: cifixer_dev
```

#### application-dev.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cifixer_dev
    username: cifixer_dev
    password: dev_password_123
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
  
  jpa:
    hibernate:
      ddl-auto: create-drop  # Recreate schema on startup
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
  
  flyway:
    enabled: false  # Disable in dev, use ddl-auto instead

logging:
  level:
    com.example.cifixer: TRACE
    org.springframework.web: DEBUG
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

management:
  endpoints:
    web:
      exposure:
        include: "*"  # Expose all actuator endpoints in dev
  endpoint:
    health:
      show-details: always

# Development-specific settings
cifixer:
  task:
    processing:
      enabled: true
      thread-pool-size: 2
      max-queue-size: 50
  
  validation:
    enabled: true
    timeout-seconds: 120
  
  cleanup:
    enabled: false  # Don't clean up in dev
    
  webhook:
    signature-validation: false  # Disable for easier testing
```

### Staging Environment

#### .env.staging
```bash
# Staging Environment Configuration

# Database Configuration
POSTGRES_DB=cifixer_staging
POSTGRES_USER=cifixer_staging
POSTGRES_PASSWORD=staging_secure_password_456

# Application Configuration
SPRING_PROFILES_ACTIVE=staging
SERVER_PORT=8080
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_EXAMPLE_CIFIXER=DEBUG

# GitHub Integration
GITHUB_TOKEN=ghp_staging_token_here
GITHUB_BASE_URL=https://api.github.com

# External API Configuration - OpenAI (balanced performance)
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-staging_openai_key_here
LLM_API_MODEL=gpt-4

# Webhook Security
WEBHOOK_SECRET=staging_webhook_secret_very_secure_789
WEBHOOK_SIGNATURE_VALIDATION_ENABLED=true

# Email Configuration (real SMTP for staging)
SPRING_MAIL_HOST=smtp.company.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=ci-fixer-staging@company.com
SPRING_MAIL_PASSWORD=staging_email_password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
NOTIFICATION_FROM_EMAIL=ci-fixer-staging@company.com
NOTIFICATION_TO_EMAILS=staging-team@company.com,devops@company.com

# Working Directory
WORK_DIR=/app/work

# Staging-specific settings
CLEANUP_ENABLED=true
CLEANUP_RETENTION_DAYS=7
TASK_RETRY_ENABLED=true
MAX_CONCURRENT_TASKS=5
```

#### docker-compose.staging.yml
```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=staging
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - LLM_API_BASE_URL=${LLM_API_BASE_URL}
      - LLM_API_KEY=${LLM_API_KEY}
      - LLM_API_MODEL=${LLM_API_MODEL}
      - WEBHOOK_SECRET=${WEBHOOK_SECRET}
      - WEBHOOK_SIGNATURE_VALIDATION_ENABLED=${WEBHOOK_SIGNATURE_VALIDATION_ENABLED}
      - SPRING_MAIL_HOST=${SPRING_MAIL_HOST}
      - SPRING_MAIL_PORT=${SPRING_MAIL_PORT}
      - SPRING_MAIL_USERNAME=${SPRING_MAIL_USERNAME}
      - SPRING_MAIL_PASSWORD=${SPRING_MAIL_PASSWORD}
      - NOTIFICATION_FROM_EMAIL=${NOTIFICATION_FROM_EMAIL}
      - NOTIFICATION_TO_EMAILS=${NOTIFICATION_TO_EMAILS}
      - WORK_DIR=${WORK_DIR}
      - JAVA_OPTS=-Xmx1536m -Xms512m -XX:+UseG1GC
    volumes:
      - ./work:/app/work
      - ./logs:/app/logs
    depends_on:
      - db
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  db:
    image: postgres:14
    environment:
      - POSTGRES_DB=${POSTGRES_DB}
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    volumes:
      - postgres_staging_data:/var/lib/postgresql/data
      - ./docker/init-scripts:/docker-entrypoint-initdb.d
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Nginx reverse proxy for staging
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/staging.conf:/etc/nginx/conf.d/default.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - app
    restart: unless-stopped

volumes:
  postgres_staging_data:

networks:
  default:
    name: cifixer_staging
```

#### application-staging.yml
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  jpa:
    hibernate:
      ddl-auto: validate  # Validate schema matches entities
    show-sql: false
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true

logging:
  level:
    com.example.cifixer: DEBUG
    org.springframework.web: INFO
    org.springframework.security: INFO
  file:
    name: /app/logs/application.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

# Staging-specific settings
cifixer:
  task:
    processing:
      enabled: true
      thread-pool-size: 5
      max-queue-size: 100
  
  validation:
    enabled: true
    timeout-seconds: 300
  
  cleanup:
    enabled: true
    retention-days: 7
    schedule: "0 2 * * *"  # Daily at 2 AM
    
  webhook:
    signature-validation: true
```

### Production Environment

#### .env.prod
```bash
# Production Environment Configuration

# Database Configuration (use external managed database)
POSTGRES_DB=cifixer_prod
POSTGRES_USER=cifixer_prod
POSTGRES_PASSWORD=production_very_secure_password_xyz

# Application Configuration
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_COM_EXAMPLE_CIFIXER=INFO

# GitHub Integration
GITHUB_TOKEN=ghp_production_token_here
GITHUB_BASE_URL=https://api.github.com

# External API Configuration - Anthropic (high quality)
LLM_API_BASE_URL=https://api.anthropic.com/v1
LLM_API_KEY=sk-ant-production_key_here
LLM_API_MODEL=claude-3-sonnet-20240229

# Webhook Security
WEBHOOK_SECRET=production_webhook_secret_extremely_secure_abc123
WEBHOOK_SIGNATURE_VALIDATION_ENABLED=true

# Email Configuration (production SMTP)
SPRING_MAIL_HOST=smtp.company.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=ci-fixer@company.com
SPRING_MAIL_PASSWORD=production_email_password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
NOTIFICATION_FROM_EMAIL=ci-fixer@company.com
NOTIFICATION_TO_EMAILS=dev-team@company.com,ops@company.com,cto@company.com

# Working Directory
WORK_DIR=/app/work

# Production-specific settings
CLEANUP_ENABLED=true
CLEANUP_RETENTION_DAYS=30
TASK_RETRY_ENABLED=true
MAX_CONCURRENT_TASKS=10

# Security settings
SSL_ENABLED=true
SECURITY_REQUIRE_SSL=true
```

#### docker-compose.prod.yml
```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://external-db.company.com:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - LLM_API_BASE_URL=${LLM_API_BASE_URL}
      - LLM_API_KEY=${LLM_API_KEY}
      - LLM_API_MODEL=${LLM_API_MODEL}
      - WEBHOOK_SECRET=${WEBHOOK_SECRET}
      - WEBHOOK_SIGNATURE_VALIDATION_ENABLED=${WEBHOOK_SIGNATURE_VALIDATION_ENABLED}
      - SPRING_MAIL_HOST=${SPRING_MAIL_HOST}
      - SPRING_MAIL_PORT=${SPRING_MAIL_PORT}
      - SPRING_MAIL_USERNAME=${SPRING_MAIL_USERNAME}
      - SPRING_MAIL_PASSWORD=${SPRING_MAIL_PASSWORD}
      - NOTIFICATION_FROM_EMAIL=${NOTIFICATION_FROM_EMAIL}
      - NOTIFICATION_TO_EMAILS=${NOTIFICATION_TO_EMAILS}
      - WORK_DIR=${WORK_DIR}
      - JAVA_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC -XX:+UseStringDeduplication
    volumes:
      - /opt/ci-fixer/work:/app/work
      - /opt/ci-fixer/logs:/app/logs
    networks:
      - internal
    restart: unless-stopped
    deploy:
      replicas: 2  # Run multiple instances
      resources:
        limits:
          memory: 3G
          cpus: '2.0'
        reservations:
          memory: 1G
          cpus: '1.0'
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
        window: 120s
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s
    security_opt:
      - no-new-privileges:true
    read_only: true
    tmpfs:
      - /tmp
      - /app/logs

  # Load balancer
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/prod.conf:/etc/nginx/conf.d/default.conf
      - /etc/letsencrypt:/etc/letsencrypt:ro
    networks:
      - internal
      - external
    restart: unless-stopped
    depends_on:
      - app
    security_opt:
      - no-new-privileges:true

  # Monitoring
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    networks:
      - internal
    restart: unless-stopped

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana:/etc/grafana/provisioning
    networks:
      - internal
    restart: unless-stopped

volumes:
  prometheus_data:
  grafana_data:

networks:
  internal:
    driver: bridge
    internal: true
  external:
    driver: bridge
```

#### application-prod.yml
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
    out-of-order: false

server:
  port: 8080
  compression:
    enabled: true
  http2:
    enabled: true

logging:
  level:
    com.example.cifixer: INFO
    org.springframework: WARN
    org.hibernate: WARN
  file:
    name: /app/logs/application.log
    max-size: 100MB
    max-history: 30
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId},%X{spanId}] %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: never
  metrics:
    export:
      prometheus:
        enabled: true

# Production-specific settings
cifixer:
  task:
    processing:
      enabled: true
      thread-pool-size: 10
      max-queue-size: 200
      batch-size: 5
  
  validation:
    enabled: true
    timeout-seconds: 600
    parallel-execution: true
  
  cleanup:
    enabled: true
    retention-days: 30
    schedule: "0 1 * * *"  # Daily at 1 AM
    
  webhook:
    signature-validation: true
    rate-limit:
      enabled: true
      requests-per-minute: 100
  
  security:
    ssl:
      enabled: true
      require-ssl: true
    cors:
      enabled: false
```

## External API Provider Configurations

### OpenRouter Configuration

#### Cost-Effective Setup
```bash
# OpenRouter with Claude Haiku (fastest, cheapest)
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=sk-or-your_key_here
LLM_API_MODEL=anthropic/claude-3-haiku
LLM_API_MAX_TOKENS=4000
LLM_API_TEMPERATURE=0.1
```

#### Balanced Performance Setup
```bash
# OpenRouter with Claude Sonnet (balanced)
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=sk-or-your_key_here
LLM_API_MODEL=anthropic/claude-3-sonnet
LLM_API_MAX_TOKENS=4000
LLM_API_TEMPERATURE=0.1
```

#### High Performance Setup
```bash
# OpenRouter with GPT-4 (highest quality)
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=sk-or-your_key_here
LLM_API_MODEL=openai/gpt-4
LLM_API_MAX_TOKENS=4000
LLM_API_TEMPERATURE=0.1
```

### OpenAI Configuration

#### Standard Setup
```bash
# OpenAI GPT-4
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-your_openai_key_here
LLM_API_MODEL=gpt-4
LLM_API_MAX_TOKENS=4000
LLM_API_TEMPERATURE=0.1
```

#### Cost-Optimized Setup
```bash
# OpenAI GPT-3.5 Turbo (cheaper alternative)
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-your_openai_key_here
LLM_API_MODEL=gpt-3.5-turbo
LLM_API_MAX_TOKENS=4000
LLM_API_TEMPERATURE=0.1
```

### Anthropic Configuration

#### Direct Anthropic Setup
```bash
# Anthropic Claude 3 Haiku
LLM_API_BASE_URL=https://api.anthropic.com/v1
LLM_API_KEY=sk-ant-your_key_here
LLM_API_MODEL=claude-3-haiku-20240307
LLM_API_MAX_TOKENS=4000
LLM_API_TEMPERATURE=0.1
```

#### High Performance Setup
```bash
# Anthropic Claude 3 Sonnet
LLM_API_BASE_URL=https://api.anthropic.com/v1
LLM_API_KEY=sk-ant-your_key_here
LLM_API_MODEL=claude-3-sonnet-20240229
LLM_API_MAX_TOKENS=4000
LLM_API_TEMPERATURE=0.1
```

## Nginx Configuration Examples

### Development Nginx Config
```nginx
# nginx/dev.conf
server {
    listen 80;
    server_name localhost;

    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Production Nginx Config
```nginx
# nginx/prod.conf
upstream cifixer_backend {
    server app:8080 max_fails=3 fail_timeout=30s;
}

# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name ci-fixer.company.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS server
server {
    listen 443 ssl http2;
    server_name ci-fixer.company.com;

    # SSL configuration
    ssl_certificate /etc/letsencrypt/live/ci-fixer.company.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/ci-fixer.company.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # Security headers
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/m;
    limit_req_zone $binary_remote_addr zone=webhook:10m rate=60r/m;

    # Main application
    location / {
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://cifixer_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }

    # Webhook endpoint (higher rate limit)
    location /webhooks/ {
        limit_req zone=webhook burst=100 nodelay;
        proxy_pass http://cifixer_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Health check endpoint (no rate limit)
    location /actuator/health {
        proxy_pass http://cifixer_backend;
        access_log off;
    }

    # Block admin endpoints from external access
    location /admin/ {
        deny all;
        return 403;
    }
}
```

## Monitoring Configuration

### Prometheus Configuration
```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alert_rules.yml"

scrape_configs:
  - job_name: 'ci-fixer'
    static_configs:
      - targets: ['app:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

### Grafana Dashboard Configuration
```json
{
  "dashboard": {
    "title": "CI Fixer Metrics",
    "panels": [
      {
        "title": "Build Processing Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(cifixer_builds_total[5m])",
            "legendFormat": "Builds/sec"
          }
        ]
      },
      {
        "title": "Task Success Rate",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(cifixer_tasks_total{status=\"completed\"}[5m]) / rate(cifixer_tasks_total[5m]) * 100",
            "legendFormat": "Success Rate %"
          }
        ]
      },
      {
        "title": "API Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(cifixer_api_duration_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      }
    ]
  }
}
```

## Deployment Scripts

### Development Deployment
```bash
#!/bin/bash
# deploy-dev.sh

set -e

echo "Deploying CI Fixer to Development..."

# Load development environment
export $(cat .env.dev | xargs)

# Build and start services
docker-compose -f docker-compose.yml -f docker-compose.dev.yml build
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Wait for services to be ready
echo "Waiting for services to start..."
sleep 30

# Check health
curl -f http://localhost:8080/actuator/health || {
    echo "Health check failed!"
    docker-compose -f docker-compose.yml -f docker-compose.dev.yml logs app
    exit 1
}

echo "Development deployment completed successfully!"
echo "Application: http://localhost:8080"
echo "MailHog UI: http://localhost:8025"
echo "PgAdmin: http://localhost:8081"
```

### Production Deployment
```bash
#!/bin/bash
# deploy-prod.sh

set -e

echo "Deploying CI Fixer to Production..."

# Backup current deployment
./backup-prod.sh

# Load production environment
export $(cat .env.prod | xargs)

# Pull latest images
docker-compose -f docker-compose.yml -f docker-compose.prod.yml pull

# Build application
docker-compose -f docker-compose.yml -f docker-compose.prod.yml build app

# Rolling update
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps app

# Wait for health check
echo "Waiting for application to be ready..."
for i in {1..30}; do
    if curl -f https://ci-fixer.company.com/actuator/health; then
        echo "Application is healthy!"
        break
    fi
    echo "Attempt $i/30 failed, waiting..."
    sleep 10
done

# Update other services
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

echo "Production deployment completed successfully!"
```

For more information, see:
- [Deployment Guide](DEPLOYMENT.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [Security Best Practices](SECURITY.md)
- [User Guide](USER_GUIDE.md)