#!/usr/bin/env bash
#
# Deploy frontend/ to Heroku via Cloud Native Buildpacks + container registry.
#
# Prereqs:
#   - docker running
#   - pack CLI installed
#   - heroku CLI authenticated (heroku login)
#   - Heroku app already created with: heroku create panas-web --stack container
#   - heroku config:set API_URL=$(heroku apps:info quiniela-panas-api --json | jq -r .app.web_url | sed 's:/*$::') --app panas-web
#     (or set it manually — get the real URL from `heroku apps:info quiniela-panas-api`,
#     since modern Heroku adds a random suffix to .herokuapp.com URLs)
#
# Usage: bin/deploy-frontend.sh
#
set -euo pipefail

APP_NAME=panas-web
IMAGE_TAG=panas-web
PROJECT_PATH=frontend
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
