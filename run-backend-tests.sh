#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$SCRIPT_DIR/mvnw" ]]; then
  BACKEND_DIR="$SCRIPT_DIR"
elif [[ -f "$SCRIPT_DIR/backend/mvnw" ]]; then
  BACKEND_DIR="$SCRIPT_DIR/backend"
else
  BACKEND_DIR="$HOME/IdeaProjects/enterprise-content-management-backend/backend"
fi

if [[ ! -f "$BACKEND_DIR/mvnw" ]]; then
  echo "Could not find backend/mvnw. Place this script in your backend directory."
  exit 1
fi

if ! command -v colima >/dev/null 2>&1; then
  echo "Colima is not installed or is not on PATH."
  exit 1
fi

if ! colima status >/dev/null 2>&1; then
  echo "Colima is not running. Start it with: colima start"
  exit 1
fi

COLIMA_SOCKET="$HOME/.colima/default/docker.sock"
if [[ ! -S "$COLIMA_SOCKET" ]]; then
  echo "Docker socket not found: $COLIMA_SOCKET"
  echo "If you use a non-default Colima profile, update COLIMA_SOCKET in this script."
  exit 1
fi

export DOCKER_HOST="unix://$COLIMA_SOCKET"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not reachable through Colima."
  exit 1
fi

cd "$BACKEND_DIR"
echo "Running Maven tests using Colima..."

if ./mvnw clean test; then
  echo "Backend tests passed."
else
  echo
  echo "Tests failed. If the logs still show Ryuk Docker socket mount errors,"
  echo "retry using: TESTCONTAINERS_RYUK_DISABLED=true ./run-backend-tests.sh"
  exit 1
fi
