#!/bin/sh
# Health check script for CI Fixer application

set -e

# Configuration
HEALTH_URL="http://localhost:8080/actuator/health"
TIMEOUT=10
MAX_RETRIES=3

# Function to check application health
check_health() {
    local retry=0
    
    while [ $retry -lt $MAX_RETRIES ]; do
        echo "Health check attempt $((retry + 1))/$MAX_RETRIES"
        
        # Use wget with timeout
        if wget --quiet --timeout=$TIMEOUT --tries=1 --spider "$HEALTH_URL" 2>/dev/null; then
            echo "✓ Application is healthy"
            return 0
        fi
        
        retry=$((retry + 1))
        if [ $retry -lt $MAX_RETRIES ]; then
            echo "⚠ Health check failed, retrying in 2 seconds..."
            sleep 2
        fi
    done
    
    echo "✗ Application health check failed after $MAX_RETRIES attempts"
    return 1
}

# Function to check database connectivity
check_database() {
    echo "Checking database connectivity..."
    
    # Try to connect to the health endpoint which includes database status
    if wget --quiet --timeout=$TIMEOUT --tries=1 -O- "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "✓ Database connectivity is healthy"
        return 0
    else
        echo "✗ Database connectivity check failed"
        return 1
    fi
}

# Main health check
echo "Starting CI Fixer health check..."

if check_health && check_database; then
    echo "✓ All health checks passed"
    exit 0
else
    echo "✗ Health check failed"
    exit 1
fi