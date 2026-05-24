#!/usr/bin/env bash
#
# Deploy frontend/ to GCP Cloud Run via Cloud Native Buildpacks + Artifact Registry.
#
# See bin/deploy-backend.sh for notes on the CNB-on-Cloud-Run story
# (no wrapper Dockerfile needed, unlike Heroku Cedar container).
#
# Prereqs:
#   - docker running
#   - pack CLI installed
#   - gcloud CLI authenticated as the project owner
#   - The IaC has been applied (creates Artifact Registry repo + Cloud Run service)
#
# Usage: bin/deploy-frontend.sh
#
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

read_tofu_output() {
  (cd iac && tofu output -raw "$1")
}

PROJECT_ID=$(read_tofu_output project_id)
REGION=$(read_tofu_output region)
REPO_URL=$(read_tofu_output artifact_registry_repo_url)
SERVICE_NAME=$(read_tofu_output web_service_name)

GIT_SHA=$(git rev-parse --short HEAD)
if ! git diff --quiet HEAD 2>/dev/null || ! git diff --quiet --cached 2>/dev/null; then
  GIT_SHA="${GIT_SHA}-dirty"
fi

IMAGE_REF="${REPO_URL}/${SERVICE_NAME}:${GIT_SHA}"
BUILDER=heroku/builder:26

echo "==> Project:  $PROJECT_ID"
echo "==> Region:   $REGION"
echo "==> Service:  $SERVICE_NAME"
echo "==> Image:    $IMAGE_REF"
echo "==> Builder:  $BUILDER"
echo

echo "==> Building OCI image with pack"
pack build "$IMAGE_REF" --builder "$BUILDER" --path frontend/

echo "==> Authenticating Docker with Artifact Registry"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

echo "==> Pushing image"
docker push "$IMAGE_REF"

echo "==> Deploying to Cloud Run"
gcloud run deploy "$SERVICE_NAME" \
  --image="$IMAGE_REF" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --quiet

echo
echo "==> Done."
echo -n "Service URL: "
gcloud run services describe "$SERVICE_NAME" \
  --region="$REGION" --project="$PROJECT_ID" \
  --format='value(status.url)'
echo
echo "Logs: gcloud run services logs tail $SERVICE_NAME --region=$REGION --project=$PROJECT_ID"
