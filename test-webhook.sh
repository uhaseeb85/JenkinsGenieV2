#!/bin/bash

WEBHOOK_URL="http://localhost:8081/api/webhook/jenkins"
WEBHOOK_SECRET="your_webhook_secret_here"  # This should match JENKINS_WEBHOOK_SECRET in .env

PAYLOAD='{
  "job": "test-project",
  "buildNumber": 123,
  "branch": "main",
  "repoUrl": "https://github.com/uhaseeb85/Research.git",
  "commitSha": "abc123def456",
  "logs": "W0VSUk9SXSBDb21waWxhdGlvbiBmYWlsZWQ=",
  "status": "FAILURE",
  "timestamp": "2024-01-15T10:30:00Z"
}'

SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | sed 's/^.* //')

curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-Jenkins-Signature: sha256=$SIGNATURE" \
  -d "$PAYLOAD" \
  "$WEBHOOK_URL"
