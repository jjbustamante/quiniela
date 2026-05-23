#!/usr/bin/env bash
#
# Creates the GCS bucket that holds OpenTofu state for this project.
# Run ONCE per GCP project — subsequent `tofu init` finds the existing bucket.
#
# Prereqs:
#   - gcloud authenticated as a project owner
#   - gcloud config set project <PROJECT_ID>
#
# Usage: ./bootstrap.sh
#
set -euo pipefail

# Read the project from gcloud config, not from tfvars — we don't want to
# parse HCL in bash. If the wrong project is active, the create will fail
# loudly rather than silently provision in the wrong place.
PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
if [[ -z "$PROJECT_ID" ]]; then
  echo "ERROR: no active gcloud project. Run: gcloud config set project <PROJECT_ID>" >&2
  exit 1
fi

BUCKET="${PROJECT_ID}-tofu-state"
LOCATION="us-central1"

echo "==> Active gcloud project: $PROJECT_ID"
echo "==> State bucket name:     gs://$BUCKET"
echo "==> Location:              $LOCATION"
echo

# Check if the bucket already exists — bootstrap is idempotent
if gcloud storage buckets describe "gs://$BUCKET" >/dev/null 2>&1; then
  echo "Bucket gs://$BUCKET already exists — nothing to do."
  exit 0
fi

echo "==> Creating bucket..."
gcloud storage buckets create "gs://$BUCKET" \
  --project="$PROJECT_ID" \
  --location="$LOCATION" \
  --uniform-bucket-level-access \
  --public-access-prevention

echo "==> Enabling object versioning (so state history is recoverable)..."
gcloud storage buckets update "gs://$BUCKET" --versioning

echo
echo "Done. Bucket gs://$BUCKET is ready."
echo
echo "Next steps:"
echo "  1. Update providers.tf backend block if the bucket name differs from \"${PROJECT_ID}-tofu-state\""
echo "  2. cp terraform.tfvars.example terraform.tfvars  (and fill in)"
echo "  3. tofu init"
echo "  4. tofu plan"
