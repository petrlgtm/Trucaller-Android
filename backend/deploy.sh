#!/bin/bash
# Deploy script for Linode
set -euo pipefail

SERVER_HOST="${DEPLOY_HOST:?Set DEPLOY_HOST}"
SERVER_USER="${DEPLOY_USER:-root}"
JAR_NAME="trucaller-backend.jar"

echo "Building fat JAR..."
cd "$(dirname "$0")/.."
./gradlew :backend:shadowJar

echo "Uploading to $SERVER_HOST..."
scp "backend/build/libs/$JAR_NAME" "$SERVER_USER@$SERVER_HOST:/opt/trucaller/"

echo "Restarting service..."
ssh "$SERVER_USER@$SERVER_HOST" "systemctl restart trucaller-backend"

echo "Deploy complete!"
