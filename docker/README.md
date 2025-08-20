# CI Fixer Docker Deployment Guide

This guide covers deploying the Multi-Agent CI Fixer using Docker and Docker Compose.

## Prerequisites

- Docker Engine 20.10+ 
- Docker Compose 2.0+
- At least 4GB RAM available for containers
- 10GB disk space for volumes and images

## Quick Start

1. **Clone the repository and navigate to the project directory**
   ```bash
   git clone <repository-url>
   cd ci-fixer
   ```

2. **Copy and configure environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Start the services**
   ```bash
   docker-compose up -d
   ```

4. **Configure external API credentials**
   ```bash
   # Edit .env file with your API credentials
   # Required: LLM_API_KEY, LLM_API_BASE_URL, LLM_API_MODEL
   ```

5. **Verify deployment**
   ```bash
   # Check service status
   docker-compose ps
   
   # Check application health
   curl http://localhost:8080/actuator/health
   
   # Access MailHog web UI
   open http://localhost:8025
   
   # Test external API connectivity
   curl -H "Authorization: Bearer $LLM_API_KEY" $LLM_API_BASE_URL/models
   ```

## Configuration

### Environment Variables

The application uses environment variables for configuration. Copy `.env.example` to `.env` and update the values:

#### Required Configuration

```bash
# GitHub Integration (Required)
GITHUB_TOKEN=ghp_your_github_token_here

# Jenkins Webhook Secret (Required)
JENKINS_WEBHOOK_SECRET=your_webhook_secret_here

# External API Integration (Required)
LLM_API_KEY=sk-or-v1-your-api-key-here
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_MODEL=anthropic/claude-3.5-sonnet
```

#### Database Configuration

```bash
POSTGRES_DB=cifixer
POSTGRES_USER=cifixer
POSTGRES_PASSWORD=your_secure_password
```

#### External API Configuration

```bash
# OpenRouter (Recommended - Multiple models available)
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=sk-or-v1-your-key-here
LLM_API_MODEL=anthropic/claude-3.5-sonnet

# Alternative: OpenAI
# LLM_API_BASE_URL=https://api.openai.com/v1
# LLM_API_KEY=sk-your-openai-key
# LLM_API_MODEL=gpt-4

# Alternative: Anthropic
# LLM_API_BASE_URL=https://api.anthropic.com/v1
# LLM_API_KEY=sk-ant-your-key
# LLM_API_MODEL=claude-3-sonnet-20240229
```

### GitHub Token Setup

1. Go to GitHub Settings → Developer settings → Personal access tokens
2. Generate a new token with these permissions:
   - `repo` (Full control of private repositories)
   - `workflow` (Update GitHub Action workflows)
3. Copy the token to your `.env` file

### Jenkins Webhook Configuration

1. In Jenkins, go to your job configuration
2. Add a Post-build Action → "Webhook Notification"
3. Set URL to: `http://your-server:8080/webhooks/jenkins`
4. Set the shared secret in both Jenkins and your `.env` file

## Service Architecture

The deployment includes these services:

- **ci-fixer**: Main Spring Boot application (port 8080)
- **postgres**: PostgreSQL database (port 5432)
- **mailhog**: Email testing service (ports 1025/8025)

External dependencies:
- **External API**: OpenAI-compatible API service (OpenRouter, OpenAI, Anthropic, etc.)

## Available API Providers and Models

### Recommended Providers

| Provider | Base URL | Strengths | Cost |
|----------|----------|-----------|------|
| **OpenRouter** | `https://openrouter.ai/api/v1` | Multiple models, competitive pricing | Variable |
| **OpenAI** | `https://api.openai.com/v1` | High quality, reliable | Higher |
| **Anthropic** | `https://api.anthropic.com/v1` | Excellent reasoning | Medium |
| **Azure OpenAI** | `https://your-resource.openai.azure.com/...` | Enterprise features | Variable |

### Recommended Models

| Model | Provider | Strengths | Use Case |
|-------|----------|-----------|----------|
| `anthropic/claude-3.5-sonnet` | OpenRouter | Excellent code generation | Production |
| `gpt-4` | OpenAI/OpenRouter | High quality, reliable | Production |
| `anthropic/claude-3-haiku` | OpenRouter | Fast, cost-effective | Development |
| `meta-llama/codellama-70b-instruct` | OpenRouter | Code-focused | Alternative |

### API Key Setup

```bash
# OpenRouter (Recommended)
# 1. Sign up at https://openrouter.ai/
# 2. Get API key from dashboard
# 3. Set in .env: LLM_API_KEY=sk-or-v1-your-key

# OpenAI
# 1. Sign up at https://platform.openai.com/
# 2. Get API key from dashboard
# 3. Set in .env: LLM_API_KEY=sk-your-openai-key

# Anthropic
# 1. Sign up at https://console.anthropic.com/
# 2. Get API key from dashboard
# 3. Set in .env: LLM_API_KEY=sk-ant-your-key
```

## Production Deployment

### Resource Requirements

**Minimum Requirements:**
- CPU: 2 cores
- RAM: 4GB
- Disk: 20GB SSD
- Network: Reliable internet connection for API calls

**Recommended for Production:**
- CPU: 4 cores
- RAM: 8GB
- Disk: 50GB SSD
- Network: High-bandwidth connection for API reliability

### Production Configuration

1. **Use external PostgreSQL database**
   ```bash
   # In .env
   SPRING_DATASOURCE_URL=jdbc:postgresql://your-db-host:5432/cifixer
   SPRING_DATASOURCE_USERNAME=cifixer_prod
   SPRING_DATASOURCE_PASSWORD=secure_production_password
   ```

2. **Configure external SMTP**
   ```bash
   # Comment out mailhog service in docker-compose.yml
   # Add to .env:
   SPRING_MAIL_HOST=your-smtp-server.com
   SPRING_MAIL_PORT=587
   SPRING_MAIL_USERNAME=your-email@company.com
   SPRING_MAIL_PASSWORD=your-email-password
   ```

3. **Enable SSL and security features**
   ```bash
   WEBHOOK_SIGNATURE_VALIDATION=true
   SSL_VERIFICATION=true
   ```

4. **Configure log aggregation**
   ```bash
   LOGGING_LEVEL_ROOT=WARN
   LOGGING_LEVEL_COM_EXAMPLE_CIFIXER=INFO
   ```

### Production Docker Compose Override

Create `docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  ci-fixer:
    environment:
      JAVA_OPTS: "-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxRAMPercentage=75"
      SPRING_PROFILES_ACTIVE: "prod"
    deploy:
      resources:
        limits:
          memory: 3g
        reservations:
          memory: 1g
    restart: always

  ollama:
    deploy:
      resources:
        limits:
          memory: 8g
        reservations:
          memory: 4g
```

Deploy with:
```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Monitoring and Maintenance

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Database health
docker-compose exec postgres pg_isready -U cifixer

# LLM service health
curl http://localhost:11434/api/tags
```

### Logs

```bash
# View application logs
docker-compose logs -f ci-fixer

# View all service logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f postgres
```

### Backup and Recovery

```bash
# Backup database
docker-compose exec postgres pg_dump -U cifixer cifixer > backup.sql

# Restore database
docker-compose exec -T postgres psql -U cifixer cifixer < backup.sql

# Backup volumes
docker run --rm -v ci-fixer_postgres_data:/data -v $(pwd):/backup alpine tar czf /backup/postgres-backup.tar.gz /data
```

### Updates

```bash
# Update application
docker-compose pull ci-fixer
docker-compose up -d ci-fixer

# Update all services
docker-compose pull
docker-compose up -d
```

## Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for detailed troubleshooting guide.

## Security Considerations

1. **Change default passwords** in production
2. **Use secrets management** for sensitive values
3. **Enable firewall rules** to restrict access
4. **Regular security updates** for base images
5. **Monitor logs** for suspicious activity
6. **Use HTTPS** with reverse proxy in production

## Support

For issues and questions:
1. Check the troubleshooting guide
2. Review application logs
3. Check service health endpoints
4. Verify environment configuration