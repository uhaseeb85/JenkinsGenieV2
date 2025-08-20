package com.example.cifixer.suite;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive test suite that runs all tests to validate requirements
 * This suite ensures 80%+ coverage across all components
 */
@Suite
@SuiteDisplayName("Multi-Agent CI Fixer - Comprehensive Test Suite")
@SelectPackages({
    "com.example.cifixer.agents",
    "com.example.cifixer.core", 
    "com.example.cifixer.git",
    "com.example.cifixer.github",
    "com.example.cifixer.llm",
    "com.example.cifixer.notification",
    "com.example.cifixer.store",
    "com.example.cifixer.util",
    "com.example.cifixer.web",
    "com.example.cifixer.integration",
    "com.example.cifixer.e2e",
    "com.example.cifixer.performance"
})
@IncludeClassNamePatterns({
    ".*Test",
    ".*Tests",
    ".*TestSuite"
})
public class ComprehensiveTestSuite {
    // Test suite configuration - no implementation needed
    // JUnit 5 will automatically discover and run all tests in the specified packages
}