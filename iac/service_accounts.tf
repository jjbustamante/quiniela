# Service accounts.
#
# For now: ONE deploy SA, used by CI/CD and `bin/deploy-*.sh` to push images
# and (later) deploy Cloud Run services. While developing locally, you can
# deploy as your own user account — the SA exists so we have an identity to
# scope CI permissions to once we wire that up.
#
# Cloud Run runtime SAs (the identity the running container assumes) come
# in the next PR alongside the Cloud Run services themselves. They should be
# distinct per service so a compromised frontend can't read backend secrets.

resource "google_service_account" "deploy" {
  project      = var.project_id
  account_id   = "quiniela-deploy"
  display_name = "Quiniela 2026 — deploy SA"
  description  = "Identity used by deploy scripts / CI to push images to Artifact Registry and deploy Cloud Run services."

  depends_on = [google_project_service.enabled]
}

# Permission: push and pull from Artifact Registry. Scoped to the repo, not
# the whole project, so a leaked key can't touch other repos.
resource "google_artifact_registry_repository_iam_member" "deploy_writer" {
  project    = var.project_id
  location   = google_artifact_registry_repository.apps.location
  repository = google_artifact_registry_repository.apps.repository_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.deploy.email}"
}

# Permission: deploy and configure Cloud Run services. Project-scoped because
# `roles/run.developer` doesn't have a service-level binding — this is the
# minimum permission to do `gcloud run deploy`.
resource "google_project_iam_member" "deploy_run_developer" {
  project = var.project_id
  role    = "roles/run.developer"
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

# Permission: act as the Cloud Run runtime SAs (added in next PR). Required
# so the deploy SA can attach a runtime SA to a Cloud Run service.
# We grant it project-wide for simplicity; tighten to specific SAs later.
resource "google_project_iam_member" "deploy_sa_user" {
  project = var.project_id
  role    = "roles/iam.serviceAccountUser"
  member  = "serviceAccount:${google_service_account.deploy.email}"
}
