#!/usr/bin/env bash
#
# Deploy backend/ to Heroku via Cloud Native Buildpacks + container registry.
#
# Prereqs:
#   - docker running
#   - pack CLI installed
#   - heroku CLI authenticated (heroku login)
#   - Heroku app already created with: heroku create quiniela-panas-api --stack container
#
# Usage: bin/deploy-backend.sh
#
set -euo pipefail

APP_NAME=quiniela-panas-api
IMAGE_TAG=quiniela-panas-api
PROJECT_PATH=backend
BUILDER=heroku/builder:26

cd "$(git rev-parse --show-toplevel)"

echo "==> Building OCI image with pack (builder: $BUILDER)"
pack build "$IMAGE_TAG" --builder "$BUILDER" --path "$PROJECT_PATH"

echo "==> Tagging for Heroku container registry"
docker tag "$IMAGE_TAG" "registry.heroku.com/$APP_NAME/web"

echo "==> Logging into Heroku container registry"
heroku container:login

echo "==> Pushing image"
docker push "registry.heroku.com/$APP_NAME/web"

echo "==> Releasing on $APP_NAME"
heroku container:release web --app "$APP_NAME"

echo "==> Done. Logs: heroku logs --tail --app $APP_NAME"
