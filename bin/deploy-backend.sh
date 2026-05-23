#!/usr/bin/env bash
#
# Deploy backend/ to Heroku via Cloud Native Buildpacks + container registry.
#
# Two-stage build:
#   1. pack build produces a CNB-native image with /cnb/process/web as ENTRYPOINT.
#   2. A wrapper Dockerfile re-tags it with:
#        - ENV CNB_PLATFORM_API=0.15 (Heroku doesn't inject config vars into
#          ENTRYPOINT — only CMD gets shell-wrapped — so we bake the launcher's
#          required env var into the image itself)
#        - ENTRYPOINT [] + CMD ["/cnb/process/web"] (moves the launcher to CMD
#          so the rest of our app's runtime config vars — DATABASE_URL, etc. —
#          actually reach the Java process)
#
# Prereqs:
#   - docker running
#   - pack CLI installed
#   - heroku CLI authenticated (heroku login)
#   - Heroku app already created with: heroku create quiniela-panas-api --stack container
#   - Postgres add-on: heroku addons:create heroku-postgresql:essential-0 --app quiniela-panas-api
#
# Usage: bin/deploy-backend.sh
#
set -euo pipefail

APP_NAME=quiniela-panas-api
BASE_TAG=quiniela-panas-api:cnb
REGISTRY_TAG=registry.heroku.com/$APP_NAME/web
PROJECT_PATH=backend
BUILDER=heroku/builder:26

cd "$(git rev-parse --show-toplevel)"

echo "==> Building CNB base image (builder: $BUILDER)"
pack build "$BASE_TAG" --builder "$BUILDER" --path "$PROJECT_PATH"

echo "==> Wrapping with Heroku-compatible entrypoint"
docker build -t "$REGISTRY_TAG" - <<EOF
FROM $BASE_TAG
ENV CNB_PLATFORM_API=0.15
ENTRYPOINT []
CMD ["/cnb/process/web"]
EOF

echo "==> Logging into Heroku container registry"
heroku container:login

echo "==> Pushing image"
docker push "$REGISTRY_TAG"

echo "==> Releasing on $APP_NAME"
heroku container:release web --app "$APP_NAME"

echo "==> Done. Logs: heroku logs --tail --app $APP_NAME"
