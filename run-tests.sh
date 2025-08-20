#!/bin/bash

# Multi-Agent CI Fixer - Comprehensive Test Execution Script

set -e

echo "=========================================="
echo "Multi-Agent CI Fixer - Test Suite Runner"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed or not in PATH"
    exit 1
fi

# Check if Docker is available (for Testcontainers)
if ! command -v docker &> /dev/null; then
    print_warning "Docker is not available. Integration tests may fail."
fi

# Clean previous builds
print_status "Cleaning previous builds..."
mvn clean

# Run unit tests with coverage
print_status "Running unit tests with coverage..."
mvn test -Dspring.profiles.active=test

# Check if unit tests passed
if [ $? -eq 0 ]; then
    print_success "Unit tests passed"
else
    print_error "Unit tests failed"
    exit 1
fi

# Run integration tests
print_status "Running integration tests..."
mvn verify -Dspring.profiles.active=test

# Check if integration tests passed
if [ $? -eq 0 ]; then
    print_success "Integration tests passed"
else
    print_error "Integration tests failed"
    exit 1
fi

# Generate coverage report
print_status "Generating coverage report..."
mvn jacoco:report

# Check coverage requirements
print_status "Checking coverage requirements (80% minimum)..."
mvn jacoco:check

if [ $? -eq 0 ]; then
    print_success "Coverage requirements met (80%+)"
else
    print_warning "Coverage requirements not met. Check target/site/jacoco/index.html for details."
fi

# Run performance tests (optional)
if [ "$1" = "--performance" ]; then
    print_status "Running performance tests..."
    mvn test -Dtest="**/*PerformanceTest" -Dspring.profiles.active=test
    
    if [ $? -eq 0 ]; then
        print_success "Performance tests passed"
    else
        print_warning "Performance tests failed or had issues"
    fi
fi

# Run comprehensive test suite
if [ "$1" = "--comprehensive" ]; then
    print_status "Running comprehensive test suite..."
    mvn test -Dtest="ComprehensiveTestSuite" -Dspring.profiles.active=test
    
    if [ $? -eq 0 ]; then
        print_success "Comprehensive test suite passed"
    else
        print_error "Comprehensive test suite failed"
        exit 1
    fi
fi

# Generate test reports
print_status "Test execution completed. Reports available at:"
echo "  - Coverage Report: target/site/jacoco/index.html"
echo "  - Surefire Reports: target/surefire-reports/"
echo "  - Failsafe Reports: target/failsafe-reports/"

print_success "All tests completed successfully!"

# Summary
echo ""
echo "=========================================="
echo "Test Execution Summary"
echo "=========================================="
echo "✅ Unit Tests: PASSED"
echo "✅ Integration Tests: PASSED"
echo "✅ Coverage Requirements: MET (80%+)"

if [ "$1" = "--performance" ]; then
    echo "✅ Performance Tests: COMPLETED"
fi

if [ "$1" = "--comprehensive" ]; then
    echo "✅ Comprehensive Suite: PASSED"
fi

echo "=========================================="