#!/usr/bin/env bash
#
# Run the backend locally with secrets sourced from backend/.env (gitignored).
#
# First time:
#   cp backend/.env.example backend/.env
#   # fill in NEXTAUTH_SECRET to match frontend/.env.local's AUTH_SECRET
#
# Then any time:
#   bin/dev-backend.sh
#
# Prereqs: Java 25 active (e.g. `sdk use java 25.0.3-tem`), local Postgres
# running (`cd backend && docker compose up -d`).
#
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if [[ ! -f backend/.env ]]; then
  echo "ERROR: backend/.env not found." >&2
  echo >&2
  echo "  cp backend/.env.example backend/.env" >&2
  echo "  # then fill in NEXTAUTH_SECRET (must match frontend/.env.local AUTH_SECRET)" >&2
  exit 1
fi

# Auto-export so sourced values become env vars for the Java child process.
set -a
# shellcheck disable=SC1091
source backend/.env
set +a

cd backend
exec ./mvnw spring-boot:run
