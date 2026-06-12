# Enable the GCP APIs the project needs. Each `google_project_service`
# is essentially "click the Enable button on this API". Free to enable;
# usage of the underlying service is what costs money.
#
# disable_on_destroy = false → `tofu destroy` won't try to disable APIs.
# Disabling can break other things in the project (the OAuth client, etc.)
# and is rarely what you want.

locals {
  enabled_apis = [
    "artifactregistry.googleapis.com",     # Docker image registry
    "run.googleapis.com",                  # Cloud Run (frontend + backend services)
    "sqladmin.googleapis.com",             # Cloud SQL (Postgres) — used in next PR
    "secretmanager.googleapis.com",        # Secret Manager — used in next PR
    "iam.googleapis.com",                  # Service accounts + IAM bindings
    "iamcredentials.googleapis.com",       # Required for SA impersonation
    "cloudresourcemanager.googleapis.com", # Project-level API calls
    "billingbudgets.googleapis.com",       # Budget alerts — used in a later PR
    "aiplatform.googleapis.com",           # Vertex AI Gemini — powers Octopus Paul predictions
    "cloudscheduler.googleapis.com",       # daily sync planner
    "cloudtasks.googleapis.com",           # per-match result-check tasks
  ]
}

resource "google_project_service" "enabled" {
  for_each = toset(local.enabled_apis)

  project            = var.project_id
  service            = each.key
  disable_on_destroy = false
}
