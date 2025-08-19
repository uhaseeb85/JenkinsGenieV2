/**
 * Agent implementations for the multi-agent CI fixer system.
 * Each agent is responsible for a specific domain of the CI fixing process:
 * - PlannerAgent: Analyzes build logs and creates fix plans
 * - RepoAgent: Handles Git operations
 * - RetrieverAgent: Identifies and ranks candidate files
 * - CodeFixAgent: Generates and applies code patches
 * - ValidatorAgent: Validates fixes by running builds
 * - PrAgent: Creates GitHub pull requests
 * - NotificationAgent: Sends notifications to stakeholders
 */
package com.example.cifixer.agents;