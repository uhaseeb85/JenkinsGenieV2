# Security Best Practices

## Overview

This document outlines security best practices for deploying and operating the Multi-Agent CI Fixer. The system handles sensitive information including API keys, repository access tokens, and code modifications, making security a critical consideration.

## Authentication and Authorization

### 1. API Key Management

#### External API Keys

**Storage:**
- Store API keys in environment variables, never in code or configuration files
- Use Docker secrets or external secret management systems in production
- Rotate API keys regularly (quarterly recommended)

```bash
# Good: Environment variable
LLM_API_KEY=sk-your-secret-key

# Bad: Hardcoded in configuration
llm.api.key=sk-your-secret-key  # Never do this
```

**Access Control:**
- Use least-privilege API keys with minimal required permissions
- Monitor API key usage and set up alerts for unusual activity
- Implement rate limiting to prevent abuse

#### GitHub/Git Tokens

**Token Permissions:**
- Use fine-grained personal access tokens with minimal repository permissions
- Required permissions: `Contents: Write`, `Pull requests: Write`, `Metadata: Read`
- Avoid using tokens with admin or organization-level permissions

```bash
# GitHub token with minimal permissions
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Git credentials for HTTPS access
GIT_USERNAME=ci-fixer-bot
GIT_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 2. Webhook Security

#### HMAC Signature Validation

Always enable webhook signature validation in production:

```yaml
webhook:
  signature:
    validation:
      enabled: true  # Never disable in production
  secret: ${WEBHOOK_SECRET}
```

**Secret Generation:**
```bash
# Generate cryptographically secure webhook secret
openssl rand -hex 32
```

**Signature Verification Process:**
1. Generate HMAC-SHA256 signature using shared secret
2. Compare with signature in `X-Jenkins-Signature` header
3. Reject requests with invalid or missing signatures

#### Network Security

**Firewall Configuration:**
```bash
# Allow only necessary ports
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP (redirect to HTTPS)
sudo ufw allow 443/tcp   # HTTPS
sudo ufw deny 8080/tcp   # Block direct access to application
sudo ufw enable
```

**Reverse Proxy Setup:**
```nginx
# nginx configuration for SSL termination
server {
    listen 443 ssl http2;
    server_name ci-fixer.yourcompany.com;

    ssl_certificate /etc/ssl/certs/ci-fixer.crt;
    ssl_certificate_key /etc/ssl/private/ci-fixer.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    ssl_prefer_server_ciphers off;

    # Security headers
    add_header Strict-Transport-Security "max-age=63072000" always;
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Rate limiting
        limit_req zone=api burst=10 nodelay;
    }
}

# Rate limiting configuration
http {
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/m;
}
```

## Data Protection

### 1. Database Security

#### Connection Security

```yaml
# Use SSL for database connections
spring:
  datasource:
    url: jdbc:postgresql://db:5432/cifixer?sslmode=require
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
```

#### Database Hardening

**PostgreSQL Configuration:**
```postgresql
# postgresql.conf
ssl = on
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'
ssl_ca_file = 'ca.crt'

# Restrict connections
listen_addresses = 'localhost'
max_connections = 100

# Enable logging
log_connections = on
log_disconnections = on
log_statement = 'mod'
```

**User Permissions:**
```sql
-- Create dedicated user with minimal permissions
CREATE USER cifixer WITH PASSWORD 'secure_password';
CREATE DATABASE cifixer OWNER cifixer;

-- Grant only necessary permissions
GRANT CONNECT ON DATABASE cifixer TO cifixer;
GRANT USAGE ON SCHEMA public TO cifixer;
GRANT CREATE ON SCHEMA public TO cifixer;

-- Revoke unnecessary permissions
REVOKE ALL ON DATABASE postgres FROM cifixer;
```

### 2. Sensitive Data Handling

#### Secret Redaction

The application automatically redacts sensitive information in logs:

```java
@Component
public class SecretManager {
    
    public String redactSecret(String secret) {
        if (secret == null || secret.length() < 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****";
    }
    
    public void logSafeValue(String key, String value) {
        if (isSensitiveKey(key)) {
            log.info("{}: {}", key, redactSecret(value));
        } else {
            log.info("{}: {}", key, value);
        }
    }
}
```

#### Data Encryption

**At Rest:**
- Use encrypted storage for database volumes
- Encrypt backup files before storing
- Consider database-level encryption for sensitive columns

```bash
# Encrypt database backups
pg_dump -U cifixer cifixer | gpg --cipher-algo AES256 --compress-algo 1 --symmetric --output backup.sql.gpg
```

**In Transit:**
- Use HTTPS/TLS for all external communications
- Enable SSL for database connections
- Use SSH for Git operations when possible

### 3. Code Security

#### Input Validation

**Webhook Payload Validation:**
```java
@Component
public class InputValidator {
    
    public void validateWebhookPayload(JenkinsWebhookPayload payload) {
        // Validate required fields
        if (payload.getJob() == null || payload.getJob().trim().isEmpty()) {
            throw new ValidationException("Job name is required");
        }
        
        // Sanitize job name to prevent injection
        if (!payload.getJob().matches("^[a-zA-Z0-9_-]+$")) {
            throw new ValidationException("Invalid job name format");
        }
        
        // Validate repository URL format
        if (!isValidRepositoryUrl(payload.getRepoUrl())) {
            throw new ValidationException("Invalid repository URL");
        }
        
        // Validate commit SHA format
        if (!payload.getCommitSha().matches("^[a-f0-9]{40}$")) {
            throw new ValidationException("Invalid commit SHA format");
        }
    }
    
    private boolean isValidRepositoryUrl(String url) {
        return url.matches("^https://github\\.com/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+\\.git$") ||
               url.matches("^git@github\\.com:[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+\\.git$");
    }
}
```

#### Patch Safety Validation

**File Path Validation:**
```java
@Component
public class PatchSafetyValidator {
    
    private static final List<String> ALLOWED_PATHS = Arrays.asList(
        "src/main/java/",
        "src/test/java/",
        "pom.xml",
        "build.gradle",
        "src/main/resources/application.yml",
        "src/main/resources/application.properties"
    );
    
    public void validatePatch(String filePath, String diff) {
        // Validate file path is within allowed directories
        if (!isAllowedPath(filePath)) {
            throw new SecurityException("File path not allowed: " + filePath);
        }
        
        // Prevent directory traversal
        if (filePath.contains("..") || filePath.contains("~")) {
            throw new SecurityException("Directory traversal detected: " + filePath);
        }
        
        // Validate diff content
        validateDiffContent(diff);
        
        // Limit patch size
        if (diff.split("\n").length > 500) {
            throw new SecurityException("Patch too large");
        }
    }
    
    private void validateDiffContent(String diff) {
        // Prevent dangerous operations
        List<String> dangerousPatterns = Arrays.asList(
            "rm -rf",
            "DELETE FROM",
            "DROP TABLE",
            "System.exit",
            "Runtime.getRuntime",
            "ProcessBuilder",
            "exec\\(",
            "eval\\(",
            "new File\\(\"/",
            "Files.delete"
        );
        
        for (String pattern : dangerousPatterns) {
            if (diff.matches(".*" + pattern + ".*")) {
                throw new SecurityException("Dangerous operation detected: " + pattern);
            }
        }
    }
}
```

## Network Security

### 1. Container Security

#### Docker Security

**Base Image Security:**
```dockerfile
# Use official, minimal base images
FROM openjdk:8-jre-alpine

# Create non-root user
RUN addgroup -g 1001 app && adduser -D -s /bin/sh -u 1001 -G app app

# Set working directory
WORKDIR /app

# Copy application files
COPY --chown=app:app target/ci-fixer.jar app.jar

# Switch to non-root user
USER app

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Start application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Container Hardening:**
```yaml
# docker-compose.yml security configuration
services:
  app:
    security_opt:
      - no-new-privileges:true
    read_only: true
    tmpfs:
      - /tmp
      - /app/logs
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
    user: "1001:1001"
```

#### Network Isolation

```yaml
# Network segmentation
networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true

services:
  nginx:
    networks:
      - frontend
      - backend
  
  app:
    networks:
      - backend
    
  db:
    networks:
      - backend
```

### 2. SSL/TLS Configuration

#### Certificate Management

**Let's Encrypt Setup:**
```bash
# Install certbot
sudo apt-get install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d ci-fixer.yourcompany.com

# Auto-renewal
echo "0 12 * * * /usr/bin/certbot renew --quiet" | sudo crontab -
```

**Manual Certificate Setup:**
```bash
# Generate private key
openssl genrsa -out ci-fixer.key 2048

# Generate certificate signing request
openssl req -new -key ci-fixer.key -out ci-fixer.csr

# Generate self-signed certificate (for testing)
openssl x509 -req -days 365 -in ci-fixer.csr -signkey ci-fixer.key -out ci-fixer.crt
```

## Access Control

### 1. Administrative Access

#### SSH Hardening

```bash
# /etc/ssh/sshd_config
Port 2222                    # Change default port
PermitRootLogin no          # Disable root login
PasswordAuthentication no   # Use key-based auth only
PubkeyAuthentication yes
MaxAuthTries 3
ClientAliveInterval 300
ClientAliveCountMax 2

# Restart SSH service
sudo systemctl restart sshd
```

#### Sudo Configuration

```bash
# Create dedicated user for CI Fixer management
sudo useradd -m -s /bin/bash cifixer-admin
sudo usermod -aG docker cifixer-admin

# Configure sudo access
echo "cifixer-admin ALL=(ALL) NOPASSWD: /usr/bin/docker-compose, /usr/bin/systemctl" | sudo tee /etc/sudoers.d/cifixer-admin
```

### 2. Application Access Control

#### Admin Endpoints Security

```java
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    @PostMapping("/retry/{taskId}")
    @PreAuthorize("hasAuthority('TASK_RETRY')")
    public ResponseEntity<Void> retryTask(@PathVariable Long taskId) {
        // Implementation
    }
    
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('METRICS_READ')")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        // Implementation
    }
}
```

#### API Rate Limiting

```java
@Component
public class RateLimitingFilter implements Filter {
    
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final int maxRequestsPerMinute = 60;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String clientIp = getClientIp(httpRequest);
        
        if (isRateLimited(clientIp)) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            return;
        }
        
        chain.doFilter(request, response);
    }
}
```

## Monitoring and Auditing

### 1. Security Monitoring

#### Log Analysis

```bash
# Monitor failed authentication attempts
grep "Authentication failed" /var/log/ci-fixer/application.log

# Monitor suspicious API calls
grep "Rate limit exceeded" /var/log/ci-fixer/application.log

# Monitor patch application failures
grep "Patch validation failed" /var/log/ci-fixer/application.log
```

#### Security Metrics

```java
@Component
public class SecurityMetrics {
    
    private final Counter authFailures = Counter.builder("security.auth.failures")
        .description("Number of authentication failures")
        .register(Metrics.globalRegistry);
    
    private final Counter webhookValidationFailures = Counter.builder("security.webhook.validation.failures")
        .description("Number of webhook validation failures")
        .register(Metrics.globalRegistry);
    
    private final Counter patchValidationFailures = Counter.builder("security.patch.validation.failures")
        .description("Number of patch validation failures")
        .register(Metrics.globalRegistry);
}
```

### 2. Audit Logging

#### Security Events

```java
@Component
public class SecurityAuditLogger {
    
    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");
    
    public void logWebhookReceived(String source, String job, boolean signatureValid) {
        auditLog.info("WEBHOOK_RECEIVED source={} job={} signature_valid={}", 
            source, job, signatureValid);
    }
    
    public void logPatchApplied(String buildId, String filePath, boolean successful) {
        auditLog.info("PATCH_APPLIED build_id={} file_path={} successful={}", 
            buildId, filePath, successful);
    }
    
    public void logApiCall(String provider, String model, boolean successful) {
        auditLog.info("API_CALL provider={} model={} successful={}", 
            provider, model, successful);
    }
}
```

## Incident Response

### 1. Security Incident Procedures

#### Immediate Response

1. **Isolate the system:**
   ```bash
   # Stop all services
   docker-compose down
   
   # Block network access
   sudo iptables -A INPUT -j DROP
   sudo iptables -A OUTPUT -j DROP
   ```

2. **Preserve evidence:**
   ```bash
   # Create forensic backup
   sudo dd if=/dev/sda of=/mnt/backup/forensic-image.dd bs=4M
   
   # Backup logs
   tar -czf incident-logs-$(date +%Y%m%d-%H%M%S).tar.gz /var/log/ci-fixer/
   ```

3. **Assess impact:**
   ```sql
   -- Check for unauthorized changes
   SELECT * FROM patches WHERE created_at > '2024-01-01 00:00:00' ORDER BY created_at DESC;
   
   -- Check for suspicious builds
   SELECT * FROM builds WHERE status = 'COMPLETED' AND created_at > '2024-01-01 00:00:00';
   ```

#### Recovery Procedures

1. **Rotate all secrets:**
   ```bash
   # Generate new webhook secret
   WEBHOOK_SECRET=$(openssl rand -hex 32)
   
   # Update GitHub token
   # Update LLM API key
   # Update database password
   ```

2. **Review and update security:**
   ```bash
   # Update all dependencies
   docker-compose pull
   docker-compose build --no-cache
   
   # Review configuration
   # Update firewall rules
   # Review access logs
   ```

### 2. Backup and Recovery

#### Secure Backup Strategy

```bash
#!/bin/bash
# secure-backup.sh

BACKUP_DIR="/secure/backups"
DATE=$(date +%Y%m%d_%H%M%S)
GPG_RECIPIENT="backup@yourcompany.com"

# Database backup
docker-compose exec -T db pg_dump -U cifixer cifixer | \
  gzip | \
  gpg --trust-model always --encrypt -r "$GPG_RECIPIENT" > \
  "$BACKUP_DIR/db_$DATE.sql.gz.gpg"

# Configuration backup
tar -czf - .env docker-compose*.yml | \
  gpg --trust-model always --encrypt -r "$GPG_RECIPIENT" > \
  "$BACKUP_DIR/config_$DATE.tar.gz.gpg"

# Verify backup integrity
gpg --verify "$BACKUP_DIR/db_$DATE.sql.gz.gpg"
gpg --verify "$BACKUP_DIR/config_$DATE.tar.gz.gpg"

# Clean old backups (keep 30 days)
find "$BACKUP_DIR" -name "*.gpg" -mtime +30 -delete
```

## Compliance and Governance

### 1. Data Privacy

#### GDPR Compliance

- **Data Minimization:** Only collect necessary build and repository information
- **Purpose Limitation:** Use data only for CI/CD automation purposes
- **Storage Limitation:** Implement data retention policies
- **Data Subject Rights:** Provide mechanisms for data deletion

```sql
-- Data retention policy implementation
DELETE FROM builds WHERE created_at < NOW() - INTERVAL '90 days';
DELETE FROM tasks WHERE created_at < NOW() - INTERVAL '90 days';
DELETE FROM patches WHERE created_at < NOW() - INTERVAL '90 days';
```

#### Data Classification

- **Public:** Repository URLs, branch names, commit SHAs
- **Internal:** Build logs, error messages, patch content
- **Confidential:** API keys, tokens, webhook secrets
- **Restricted:** Database credentials, SSL private keys

### 2. Security Policies

#### Access Control Policy

1. **Principle of Least Privilege:** Grant minimum necessary permissions
2. **Separation of Duties:** Separate development and production access
3. **Regular Access Review:** Quarterly review of user permissions
4. **Strong Authentication:** Require MFA for administrative access

#### Change Management

1. **Code Review:** All changes require peer review
2. **Security Testing:** Automated security scans in CI/CD
3. **Deployment Approval:** Production deployments require approval
4. **Rollback Procedures:** Documented rollback procedures for incidents

For more information, see:
- [Deployment Guide](DEPLOYMENT.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [Monitoring Guide](MONITORING.md)
- [Operational Runbook](OPERATIONAL_RUNBOOK.md)