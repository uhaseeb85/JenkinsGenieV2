# Multi-Agent CI Fixer - Deployment Guide

## Overview

The Multi-Agent CI Fixer is a Spring Boot application designed to automatically fix Java Spring project build failures. This guide covers deployment using Docker with PostgreSQL and external OpenAI-compatible API integration.

## Prerequisites

- Docker and Docker Compose
- Git access to repositories you want to fix
- GitHub personal access token with repository permissions
- Access to an OpenAI-compatible API (OpenRouter, OpenAI, Anthropic, etc.)
- SMTP server for notifications (optional)

## Quick Start

1. Clone the repository:
```bash
git clone <repository-url>
cd multi-agent-ci-fixer
```

2. Copy environment configuration:
```bash
cp .env.example .env
```

3. Edit `.env` file with your configuration (see Configuration section below)

4. Start the application:
```bash
docker-compose up -d
```

5. Verify deployment:
```bash
curl http://localhost:8080/actuator/health
```

## Configuration

### Environment Variables

Create a `.env` file in the project root with the following variables:

```bash
# Database Configuration
POSTGRES_DB=cifixer
POSTGRES_USER=cifixer
POSTGRES_PASSWORD=your_secure_password

# Application Configuration
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# GitHub Integration
GITHUB_TOKEN=ghp_your_github_token
GITHUB_BASE_URL=https://api.github.com

# External API Configuration (choose one)
# OpenRouter
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=sk-or-your_openrouter_key
LLM_API_MODEL=anthropic/claude-3-haiku

# OpenAI
# LLM_API_BASE_URL=https://api.openai.com/v1
# LLM_API_KEY=sk-your_openai_key
# LLM_API_MODEL=gpt-4

# Anthropic
# LLM_API_BASE_URL=https://api.anthropic.com/v1
# LLM_API_KEY=sk-ant-your_anthropic_key
# LLM_API_MODEL=claude-3-haiku-20240307

# Webhook Security
WEBHOOK_SECRET=your_webhook_secret_key

# Email Notifications (optional)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_password
NOTIFICATION_FROM_EMAIL=ci-fixer@yourcompany.com
NOTIFICATION_TO_EMAILS=dev-team@yourcompany.com,ops@yourcompany.com

# Working Directory
WORK_DIR=/app/work

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_EXAMPLE_CIFIXER=DEBUG
```

### Docker Compose Configuration

The application uses `docker-compose.yml` for orchestration:

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - LLM_API_BASE_URL=${LLM_API_BASE_URL}
      - LLM_API_KEY=${LLM_API_KEY}
      - LLM_API_MODEL=${LLM_API_MODEL}
      - WEBHOOK_SECRET=${WEBHOOK_SECRET}
      - WORK_DIR=${WORK_DIR:-/app/work}
    volumes:
      - ./work:/app/work
      - ./logs:/app/logs
    depends_on:
      - db
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  db:
    image: postgres:14
    environment:
      - POSTGRES_DB=${POSTGRES_DB}
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/init-scripts:/docker-entrypoint-initdb.d
    ports:
      - "5432:5432"
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  mailhog:
    image: mailhog/mailhog:latest
    ports:
      - "1025:1025"  # SMTP
      - "8025:8025"  # Web UI
    restart: unless-stopped

volumes:
  postgres_data:
```

## Environment-Specific Configurations

### Development Environment

Create `docker-compose.dev.yml`:

```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      target: development
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - LOGGING_LEVEL_ROOT=DEBUG
      - SPRING_MAIL_HOST=mailhog
      - SPRING_MAIL_PORT=1025
    volumes:
      - .:/app
      - /app/target
    ports:
      - "8080:8080"
      - "5005:5005"  # Debug port
    command: ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "/app/target/ci-fixer.jar"]

  db:
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=cifixer_dev
```

Start development environment:
```bash
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

### Staging Environment

Create `docker-compose.staging.yml`:

```yaml
version: '3.8'

services:
  app:
    environment:
      - SPRING_PROFILES_ACTIVE=staging
      - LOGGING_LEVEL_ROOT=INFO
      - LOGGING_LEVEL_COM_EXAMPLE_CIFIXER=DEBUG
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'

  db:
    environment:
      - POSTGRES_DB=cifixer_staging
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: '0.25'
```

### Production Environment

Create `docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  app:
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - LOGGING_LEVEL_ROOT=WARN
      - LOGGING_LEVEL_COM_EXAMPLE_CIFIXER=INFO
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3

  db:
    environment:
      - POSTGRES_DB=cifixer_prod
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
    volumes:
      - /var/lib/postgresql/data:/var/lib/postgresql/data

  # Remove mailhog in production
  mailhog:
    deploy:
      replicas: 0
```

## External API Integration Setup

### OpenRouter Setup

1. Sign up at [OpenRouter](https://openrouter.ai/)
2. Create an API key
3. Configure environment variables:
```bash
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=sk-or-your_key_here
LLM_API_MODEL=anthropic/claude-3-haiku  # or other supported models
```

Recommended models:
- `anthropic/claude-3-haiku` - Fast and cost-effective
- `anthropic/claude-3-sonnet` - Balanced performance
- `openai/gpt-4` - High quality but more expensive

### OpenAI Setup

1. Sign up at [OpenAI](https://platform.openai.com/)
2. Create an API key
3. Configure environment variables:
```bash
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-your_openai_key
LLM_API_MODEL=gpt-4
```

### Anthropic Setup

1. Sign up at [Anthropic](https://console.anthropic.com/)
2. Create an API key
3. Configure environment variables:
```bash
LLM_API_BASE_URL=https://api.anthropic.com/v1
LLM_API_KEY=sk-ant-your_key
LLM_API_MODEL=claude-3-haiku-20240307
```

## Deployment Steps

### 1. Prepare the Environment

```bash
# Create project directory
mkdir -p /opt/ci-fixer
cd /opt/ci-fixer

# Clone repository
git clone <repository-url> .

# Create necessary directories
mkdir -p work logs data
chmod 755 work logs data
```

### 2. Configure Environment

```bash
# Copy and edit environment file
cp .env.example .env
nano .env  # Edit with your configuration
```

### 3. Build and Deploy

```bash
# Build the application
docker-compose build

# Start services
docker-compose up -d

# Check logs
docker-compose logs -f app
```

### 4. Verify Deployment

```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Check database connection
docker-compose exec db psql -U cifixer -d cifixer -c "SELECT version();"

# Check application logs
docker-compose logs app | tail -50
```

### 5. Configure Webhooks

See the [Webhook Configuration Guide](#webhook-configuration) below.

## Monitoring and Maintenance

### Health Checks

The application exposes several health check endpoints:

- `/actuator/health` - Overall application health
- `/actuator/health/db` - Database connectivity
- `/actuator/health/diskSpace` - Disk space availability
- `/actuator/info` - Application information

### Metrics

Prometheus metrics are available at `/actuator/prometheus`:

- `cifixer_tasks_total` - Total tasks processed
- `cifixer_tasks_duration_seconds` - Task processing duration
- `cifixer_builds_total` - Total builds processed
- `cifixer_api_calls_total` - External API calls made

### Log Management

Logs are written to:
- Console (captured by Docker)
- `/app/logs/application.log` (mounted volume)

Configure log rotation:
```bash
# Add to /etc/logrotate.d/ci-fixer
/opt/ci-fixer/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 644 root root
}
```

### Backup Strategy

#### Database Backup

```bash
# Create backup script
cat > /opt/ci-fixer/backup-db.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/ci-fixer/backups"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

docker-compose exec -T db pg_dump -U cifixer cifixer | gzip > $BACKUP_DIR/cifixer_$DATE.sql.gz

# Keep only last 7 days
find $BACKUP_DIR -name "cifixer_*.sql.gz" -mtime +7 -delete
EOF

chmod +x /opt/ci-fixer/backup-db.sh

# Add to crontab
echo "0 2 * * * /opt/ci-fixer/backup-db.sh" | crontab -
```

#### Configuration Backup

```bash
# Backup configuration files
tar -czf config-backup-$(date +%Y%m%d).tar.gz .env docker-compose*.yml
```

## Scaling Considerations

### Horizontal Scaling

To scale the application horizontally:

1. Use external PostgreSQL database
2. Configure shared file storage for working directories
3. Use load balancer for webhook endpoints

```yaml
# docker-compose.scale.yml
version: '3.8'

services:
  app:
    deploy:
      replicas: 3
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://external-db:5432/cifixer
    volumes:
      - nfs-storage:/app/work

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - app
```

### Resource Requirements

Minimum requirements per instance:
- CPU: 1 core
- Memory: 1GB RAM
- Disk: 10GB (for working directories and logs)
- Network: Outbound HTTPS access

Recommended for production:
- CPU: 2 cores
- Memory: 2GB RAM
- Disk: 50GB SSD
- Network: Load balancer with SSL termination

## Security Hardening

### Network Security

```yaml
# docker-compose.security.yml
version: '3.8'

services:
  app:
    networks:
      - internal
      - external
    ports: []  # Remove direct port exposure

  db:
    networks:
      - internal
    ports: []  # Remove external access

  nginx:
    networks:
      - external
    ports:
      - "443:443"
    volumes:
      - ./ssl:/etc/nginx/ssl

networks:
  internal:
    driver: bridge
    internal: true
  external:
    driver: bridge
```

### SSL Configuration

```nginx
# nginx.conf
server {
    listen 443 ssl http2;
    server_name ci-fixer.yourcompany.com;

    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;

    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Secret Management

Use Docker secrets or external secret management:

```yaml
# docker-compose.secrets.yml
version: '3.8'

services:
  app:
    secrets:
      - github_token
      - llm_api_key
      - webhook_secret
    environment:
      - GITHUB_TOKEN_FILE=/run/secrets/github_token
      - LLM_API_KEY_FILE=/run/secrets/llm_api_key
      - WEBHOOK_SECRET_FILE=/run/secrets/webhook_secret

secrets:
  github_token:
    external: true
  llm_api_key:
    external: true
  webhook_secret:
    external: true
```

## Next Steps

1. Review the [Troubleshooting Guide](TROUBLESHOOTING.md) for common issues
2. Configure webhooks using the [Webhook Configuration Guide](WEBHOOK_CONFIGURATION.md)
3. Set up monitoring using the [Monitoring Guide](MONITORING.md)
4. Review [Security Best Practices](SECURITY.md)

For operational procedures, see the [Operational Runbook](OPERATIONAL_RUNBOOK.md).