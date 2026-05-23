#!/usr/bin/env bash
#
# Deploy frontend/ to Heroku via Cloud Native Buildpacks + container registry.
#
# See bin/deploy-backend.sh for the rationale behind the two-stage build with
# the wrapper Dockerfile. Same approach here.
#
# Prereqs:
#   - docker running
#   - pack CLI installed
#   - heroku CLI authenticated (heroku login)
#   - Heroku app already created with: heroku create quiniela-panas-web --stack container
#   - API_URL config var set, e.g.:
#       heroku config:set \
#         API_URL=\$(heroku apps:info quiniela-panas-api --json | jq -r .app.web_url | sed 's:/*\$::') \
#         --app quiniela-panas-web
#     (modern Heroku adds a random suffix to .herokuapp.com URLs)
#
# Usage: bin/deploy-frontend.sh
#
set -euo pipefail

APP_NAME=quiniela-panas-web
BASE_TAG=quiniela-panas-web:cnb
REGISTRY_TAG=registry.heroku.com/$APP_NAME/web
PROJECT_PATH=frontend
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
