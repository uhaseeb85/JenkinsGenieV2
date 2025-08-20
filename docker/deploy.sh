#!/bin/bash
# CI Fixer Deployment Script
# This script helps deploy the CI Fixer application with proper configuration

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_DIR/.env"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
PROD_COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose is not installed or not in PATH"
        exit 1
    fi
    
    # Check Docker daemon
    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Function to setup environment
setup_environment() {
    log_info "Setting up environment configuration..."
    
    if [ ! -f "$ENV_FILE" ]; then
        log_warning ".env file not found, creating from template..."
        cp "$PROJECT_DIR/.env.example" "$ENV_FILE"
        log_warning "Please edit .env file with your configuration before continuing"
        log_warning "Required variables: GITHUB_TOKEN, JENKINS_WEBHOOK_SECRET"
        exit 1
    fi
    
    # Check required environment variables
    source "$ENV_FILE"
    
    if [ -z "$GITHUB_TOKEN" ] || [ "$GITHUB_TOKEN" = "ghp_your_github_token_here" ]; then
        log_error "GITHUB_TOKEN is not configured in .env file"
        exit 1
    fi
    
    if [ -z "$JENKINS_WEBHOOK_SECRET" ] || [ "$JENKINS_WEBHOOK_SECRET" = "your_webhook_secret_here" ]; then
        log_error "JENKINS_WEBHOOK_SECRET is not configured in .env file"
        exit 1
    fi
    
    log_success "Environment configuration validated"
}

# Function to create required directories
create_directories() {
    log_info "Creating required directories..."
    
    # Create data directories for production
    if [ "$ENVIRONMENT" = "prod" ]; then
        sudo mkdir -p /opt/ci-fixer/data/postgres
        sudo mkdir -p /opt/ci-fixer/data/work
        sudo mkdir -p /opt/ci-fixer/logs
        sudo chown -R $USER:$USER /opt/ci-fixer
    fi
    
    log_success "Directories created"
}

# Function to pull and build images
build_images() {
    log_info "Building and pulling Docker images..."
    
    cd "$PROJECT_DIR"
    
    if [ "$ENVIRONMENT" = "prod" ]; then
        docker-compose -f "$COMPOSE_FILE" -f "$PROD_COMPOSE_FILE" pull
        docker-compose -f "$COMPOSE_FILE" -f "$PROD_COMPOSE_FILE" build --no-cache ci-fixer
    else
        docker-compose pull
        docker-compose build --no-cache ci-fixer
    fi
    
    log_success "Images built successfully"
}

# Function to start services
start_services() {
    log_info "Starting services..."
    
    cd "$PROJECT_DIR"
    
    if [ "$ENVIRONMENT" = "prod" ]; then
        docker-compose -f "$COMPOSE_FILE" -f "$PROD_COMPOSE_FILE" up -d
    else
        docker-compose up -d
    fi
    
    log_success "Services started"
}

# Function to validate API configuration
validate_api_config() {
    log_info "Validating external API configuration..."
    
    # Check if API key is configured
    if [ -z "$LLM_API_KEY" ] || [ "$LLM_API_KEY" = "sk-or-v1-your-openrouter-key-here" ]; then
        log_error "LLM_API_KEY is not configured in .env file"
        log_error "Please set a valid API key for your chosen provider"
        exit 1
    fi
    
    # Validate API base URL
    if [ -z "$LLM_API_BASE_URL" ]; then
        log_error "LLM_API_BASE_URL is not configured in .env file"
        exit 1
    fi
    
    log_success "API configuration validated"
}

# Function to verify deployment
verify_deployment() {
    log_info "Verifying deployment..."
    
    # Wait for application to be ready
    log_info "Waiting for application to be ready..."
    for i in {1..60}; do
        if curl -f http://localhost:${APP_PORT:-8080}/actuator/health &> /dev/null; then
            break
        fi
        sleep 2
    done
    
    # Check service status
    log_info "Checking service status..."
    docker-compose ps
    
    # Test health endpoints
    log_info "Testing health endpoints..."
    
    # Application health
    if curl -f http://localhost:${APP_PORT:-8080}/actuator/health; then
        log_success "Application health check passed"
    else
        log_error "Application health check failed"
        return 1
    fi
    
    # Database health
    if docker-compose exec -T postgres pg_isready -U ${POSTGRES_USER:-cifixer}; then
        log_success "Database health check passed"
    else
        log_error "Database health check failed"
        return 1
    fi
    
    # External API connectivity (basic check)
    log_info "Checking external API connectivity..."
    if [ "$LLM_API_PROVIDER" = "openrouter" ]; then
        if curl -f -H "Authorization: Bearer $LLM_API_KEY" https://openrouter.ai/api/v1/models &> /dev/null; then
            log_success "External API connectivity check passed"
        else
            log_warning "External API connectivity check failed (may be due to rate limits)"
        fi
    else
        log_info "Skipping API connectivity check for provider: $LLM_API_PROVIDER"
    fi
    
    log_success "Deployment verification completed"
}

# Function to show deployment info
show_deployment_info() {
    log_success "Deployment completed successfully!"
    echo
    echo "=== Service URLs ==="
    echo "Application:     http://localhost:${APP_PORT:-8080}"
    echo "Health Check:    http://localhost:${APP_PORT:-8080}/actuator/health"
    echo "MailHog Web UI:  http://localhost:${MAILHOG_WEB_PORT:-8025}"
    echo "External API:    ${LLM_API_BASE_URL} (${LLM_API_PROVIDER})"
    echo
    echo "=== Useful Commands ==="
    echo "View logs:       docker-compose logs -f"
    echo "Check status:    docker-compose ps"
    echo "Stop services:   docker-compose down"
    echo "Restart app:     docker-compose restart ci-fixer"
    echo
    echo "=== Next Steps ==="
    echo "1. Configure Jenkins webhook: http://your-server:${APP_PORT:-8080}/webhooks/jenkins"
    echo "2. Test webhook with a failed build"
    echo "3. Monitor logs: docker-compose logs -f ci-fixer"
    echo "4. Check MailHog for email notifications"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [ENVIRONMENT]"
    echo
    echo "ENVIRONMENT:"
    echo "  dev   - Development environment (default)"
    echo "  prod  - Production environment"
    echo
    echo "Examples:"
    echo "  $0        # Deploy in development mode"
    echo "  $0 dev    # Deploy in development mode"
    echo "  $0 prod   # Deploy in production mode"
    echo
    echo "Options:"
    echo "  -h, --help    Show this help message"
    echo "  --no-verify   Skip deployment verification"
    echo "  --no-api-check    Skip external API connectivity check"
}

# Main deployment function
main() {
    # Parse arguments
    ENVIRONMENT="dev"
    SKIP_VERIFY=false
    SKIP_MODEL=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            prod|production)
                ENVIRONMENT="prod"
                shift
                ;;
            dev|development)
                ENVIRONMENT="dev"
                shift
                ;;
            --no-verify)
                SKIP_VERIFY=true
                shift
                ;;
            --no-api-check)
                SKIP_API_CHECK=true
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown argument: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    log_info "Starting CI Fixer deployment in $ENVIRONMENT mode..."
    
    # Run deployment steps
    check_prerequisites
    setup_environment
    create_directories
    build_images
    start_services
    
    validate_api_config
    
    if [ "$SKIP_VERIFY" = false ]; then
        verify_deployment
    fi
    
    show_deployment_info
}

# Run main function with all arguments
main "$@"