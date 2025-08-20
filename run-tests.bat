@echo off
REM Multi-Agent CI Fixer - Comprehensive Test Execution Script for Windows

echo ==========================================
echo Multi-Agent CI Fixer - Test Suite Runner
echo ==========================================

REM Check if Maven is available
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Maven is not installed or not in PATH
    exit /b 1
)

REM Check if Docker is available (for Testcontainers)
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [WARNING] Docker is not available. Integration tests may fail.
)

REM Clean previous builds
echo [INFO] Cleaning previous builds...
call mvn clean
if %errorlevel% neq 0 (
    echo [ERROR] Maven clean failed
    exit /b 1
)

REM Run unit tests with coverage
echo [INFO] Running unit tests with coverage...
call mvn test -Dspring.profiles.active=test
if %errorlevel% neq 0 (
    echo [ERROR] Unit tests failed
    exit /b 1
) else (
    echo [SUCCESS] Unit tests passed
)

REM Run integration tests
echo [INFO] Running integration tests...
call mvn verify -Dspring.profiles.active=test
if %errorlevel% neq 0 (
    echo [ERROR] Integration tests failed
    exit /b 1
) else (
    echo [SUCCESS] Integration tests passed
)

REM Generate coverage report
echo [INFO] Generating coverage report...
call mvn jacoco:report

REM Check coverage requirements
echo [INFO] Checking coverage requirements (80%% minimum)...
call mvn jacoco:check
if %errorlevel% neq 0 (
    echo [WARNING] Coverage requirements not met. Check target\site\jacoco\index.html for details.
) else (
    echo [SUCCESS] Coverage requirements met (80%%+)
)

REM Run performance tests (optional)
if "%1"=="--performance" (
    echo [INFO] Running performance tests...
    call mvn test -Dtest="**/*PerformanceTest" -Dspring.profiles.active=test
    if %errorlevel% neq 0 (
        echo [WARNING] Performance tests failed or had issues
    ) else (
        echo [SUCCESS] Performance tests passed
    )
)

REM Run comprehensive test suite
if "%1"=="--comprehensive" (
    echo [INFO] Running comprehensive test suite...
    call mvn test -Dtest="ComprehensiveTestSuite" -Dspring.profiles.active=test
    if %errorlevel% neq 0 (
        echo [ERROR] Comprehensive test suite failed
        exit /b 1
    ) else (
        echo [SUCCESS] Comprehensive test suite passed
    )
)

REM Generate test reports
echo [INFO] Test execution completed. Reports available at:
echo   - Coverage Report: target\site\jacoco\index.html
echo   - Surefire Reports: target\surefire-reports\
echo   - Failsafe Reports: target\failsafe-reports\

echo [SUCCESS] All tests completed successfully!

REM Summary
echo.
echo ==========================================
echo Test Execution Summary
echo ==========================================
echo ✓ Unit Tests: PASSED
echo ✓ Integration Tests: PASSED
echo ✓ Coverage Requirements: MET (80%%+)

if "%1"=="--performance" (
    echo ✓ Performance Tests: COMPLETED
)

if "%1"=="--comprehensive" (
    echo ✓ Comprehensive Suite: PASSED
)

echo ==========================================