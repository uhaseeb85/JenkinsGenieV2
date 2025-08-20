@echo off
REM CI Fixer Deployment Script for Windows
REM This script helps deploy the CI Fixer application with proper configuration

setlocal enabledelayedexpansion

REM Configuration
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "ENV_FILE=%PROJECT_DIR%\.env"
set "COMPOSE_FILE=%PROJECT_DIR%\docker-compose.yml"
set "PROD_COMPOSE_FILE=%PROJECT_DIR%\docker-compose.prod.yml"

REM Default values
set "ENVIRONMENT=dev"
set "SKIP_VERIFY=false"
set "SKIP_MODEL=false"

REM Parse command line arguments
:parse_args
if "%~1"=="" goto :start_deployment
if /i "%~1"=="prod" set "ENVIRONMENT=prod" & shift & goto :parse_args
if /i "%~1"=="production" set "ENVIRONMENT=prod" & shift & goto :parse_args
if /i "%~1"=="dev" set "ENVIRONMENT=dev" & shift & goto :parse_args
if /i "%~1"=="development" set "ENVIRONMENT=dev" & shift & goto :parse_args
if /i "%~1"=="--no-verify" set "SKIP_VERIFY=true" & shift & goto :parse_args
if /i "%~1"=="--no-model" set "SKIP_MODEL=true" & shift & goto :parse_args
if /i "%~1"=="-h" goto :show_usage
if /i "%~1"=="--help" goto :show_usage
echo [ERROR] Unknown argument: %~1
goto :show_usage

:start_deployment
echo [INFO] Starting CI Fixer deployment in %ENVIRONMENT% mode...

REM Check prerequisites
call :check_prerequisites
if errorlevel 1 exit /b 1

REM Setup environment
call :setup_environment
if errorlevel 1 exit /b 1

REM Build images
call :build_images
if errorlevel 1 exit /b 1

REM Start services
call :start_services
if errorlevel 1 exit /b 1

REM Validate API configuration
call :validate_api_config
if errorlevel 1 exit /b 1

REM Verify deployment
if "%SKIP_VERIFY%"=="false" (
    call :verify_deployment
    if errorlevel 1 exit /b 1
)

REM Show deployment info
call :show_deployment_info

echo [SUCCESS] Deployment completed successfully!
exit /b 0

:check_prerequisites
echo [INFO] Checking prerequisites...

REM Check Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not installed or not in PATH
    exit /b 1
)

REM Check Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose is not installed or not in PATH
    exit /b 1
)

REM Check Docker daemon
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker daemon is not running
    exit /b 1
)

echo [SUCCESS] Prerequisites check passed
exit /b 0

:setup_environment
echo [INFO] Setting up environment configuration...

if not exist "%ENV_FILE%" (
    echo [WARNING] .env file not found, creating from template...
    copy "%PROJECT_DIR%\.env.example" "%ENV_FILE%" >nul
    echo [WARNING] Please edit .env file with your configuration before continuing
    echo [WARNING] Required variables: GITHUB_TOKEN, JENKINS_WEBHOOK_SECRET
    exit /b 1
)

REM Load environment variables
for /f "usebackq tokens=1,2 delims==" %%a in ("%ENV_FILE%") do (
    if not "%%a"=="" if not "%%b"=="" (
        set "%%a=%%b"
    )
)

REM Check required variables
if "%GITHUB_TOKEN%"=="" (
    echo [ERROR] GITHUB_TOKEN is not configured in .env file
    exit /b 1
)
if "%GITHUB_TOKEN%"=="ghp_your_github_token_here" (
    echo [ERROR] GITHUB_TOKEN is not configured in .env file
    exit /b 1
)

if "%JENKINS_WEBHOOK_SECRET%"=="" (
    echo [ERROR] JENKINS_WEBHOOK_SECRET is not configured in .env file
    exit /b 1
)
if "%JENKINS_WEBHOOK_SECRET%"=="your_webhook_secret_here" (
    echo [ERROR] JENKINS_WEBHOOK_SECRET is not configured in .env file
    exit /b 1
)

if "%LLM_API_KEY%"=="" (
    echo [ERROR] LLM_API_KEY is not configured in .env file
    exit /b 1
)
if "%LLM_API_KEY%"=="sk-or-v1-your-openrouter-key-here" (
    echo [ERROR] LLM_API_KEY is not configured in .env file
    exit /b 1
)

echo [SUCCESS] Environment configuration validated
exit /b 0

:build_images
echo [INFO] Building and pulling Docker images...

cd /d "%PROJECT_DIR%"

if "%ENVIRONMENT%"=="prod" (
    docker-compose -f "%COMPOSE_FILE%" -f "%PROD_COMPOSE_FILE%" pull
    if errorlevel 1 exit /b 1
    docker-compose -f "%COMPOSE_FILE%" -f "%PROD_COMPOSE_FILE%" build --no-cache ci-fixer
    if errorlevel 1 exit /b 1
) else (
    docker-compose pull
    if errorlevel 1 exit /b 1
    docker-compose build --no-cache ci-fixer
    if errorlevel 1 exit /b 1
)

echo [SUCCESS] Images built successfully
exit /b 0

:start_services
echo [INFO] Starting services...

cd /d "%PROJECT_DIR%"

if "%ENVIRONMENT%"=="prod" (
    docker-compose -f "%COMPOSE_FILE%" -f "%PROD_COMPOSE_FILE%" up -d
) else (
    docker-compose up -d
)

if errorlevel 1 (
    echo [ERROR] Failed to start services
    exit /b 1
)

echo [SUCCESS] Services started
exit /b 0

:validate_api_config
echo [INFO] Validating external API configuration...

if "%LLM_API_BASE_URL%"=="" (
    echo [ERROR] LLM_API_BASE_URL is not configured in .env file
    exit /b 1
)

echo [SUCCESS] API configuration validated
exit /b 0

:verify_deployment
echo [INFO] Verifying deployment...

REM Wait for application to be ready
echo [INFO] Waiting for application to be ready...
if "%APP_PORT%"=="" set "APP_PORT=8080"
set /a "counter=0"
:wait_app
set /a "counter+=1"
if %counter% gtr 60 (
    echo [ERROR] Application did not become ready in time
    exit /b 1
)

curl -f http://localhost:%APP_PORT%/actuator/health >nul 2>&1
if errorlevel 1 (
    timeout /t 2 /nobreak >nul
    goto :wait_app
)

REM Check service status
echo [INFO] Checking service status...
docker-compose ps

REM Test health endpoints
echo [INFO] Testing health endpoints...

REM Application health
curl -f http://localhost:%APP_PORT%/actuator/health >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Application health check failed
    exit /b 1
) else (
    echo [SUCCESS] Application health check passed
)

REM Database health
if "%POSTGRES_USER%"=="" set "POSTGRES_USER=cifixer"
docker-compose exec -T postgres pg_isready -U %POSTGRES_USER% >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Database health check failed
    exit /b 1
) else (
    echo [SUCCESS] Database health check passed
)

REM External API connectivity (basic check)
echo [INFO] Checking external API connectivity...
if "%LLM_API_PROVIDER%"=="openrouter" (
    curl -f -H "Authorization: Bearer %LLM_API_KEY%" https://openrouter.ai/api/v1/models >nul 2>&1
    if errorlevel 1 (
        echo [WARNING] External API connectivity check failed (may be due to rate limits)
    ) else (
        echo [SUCCESS] External API connectivity check passed
    )
) else (
    echo [INFO] Skipping API connectivity check for provider: %LLM_API_PROVIDER%
)

echo [SUCCESS] Deployment verification completed
exit /b 0

:show_deployment_info
if "%APP_PORT%"=="" set "APP_PORT=8080"
if "%MAILHOG_WEB_PORT%"=="" set "MAILHOG_WEB_PORT=8025"
if "%OLLAMA_PORT%"=="" set "OLLAMA_PORT=11434"

echo.
echo === Service URLs ===
echo Application:     http://localhost:%APP_PORT%
echo Health Check:    http://localhost:%APP_PORT%/actuator/health
echo MailHog Web UI:  http://localhost:%MAILHOG_WEB_PORT%
echo External API:    %LLM_API_BASE_URL% (%LLM_API_PROVIDER%)
echo.
echo === Useful Commands ===
echo View logs:       docker-compose logs -f
echo Check status:    docker-compose ps
echo Stop services:   docker-compose down
echo Restart app:     docker-compose restart ci-fixer
echo.
echo === Next Steps ===
echo 1. Configure Jenkins webhook: http://your-server:%APP_PORT%/webhooks/jenkins
echo 2. Test webhook with a failed build
echo 3. Monitor logs: docker-compose logs -f ci-fixer
echo 4. Check MailHog for email notifications
exit /b 0

:show_usage
echo Usage: %~nx0 [ENVIRONMENT]
echo.
echo ENVIRONMENT:
echo   dev   - Development environment (default)
echo   prod  - Production environment
echo.
echo Examples:
echo   %~nx0        # Deploy in development mode
echo   %~nx0 dev    # Deploy in development mode
echo   %~nx0 prod   # Deploy in production mode
echo.
echo Options:
echo   -h, --help    Show this help message
echo   --no-verify   Skip deployment verification
echo   --no-api-check    Skip external API connectivity check
exit /b 0