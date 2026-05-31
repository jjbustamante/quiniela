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

# ─── Cloud Run runtime service accounts ─────────────────────────────────────
# One SA per Cloud Run service so a compromise in the frontend can't reach
# backend-only secrets, and vice versa. Each SA gets the minimum permissions
# its service actually needs.

resource "google_service_account" "api_runtime" {
  project      = var.project_id
  account_id   = "quiniela-api-runtime"
  display_name = "Quiniela 2026 — backend runtime SA"
  description  = "Identity that the quiniela-api Cloud Run service runs as."

  depends_on = [google_project_service.enabled]
}

resource "google_service_account" "web_runtime" {
  project      = var.project_id
  account_id   = "quiniela-web-runtime"
  display_name = "Quiniela 2026 — frontend runtime SA"
  description  = "Identity that the quiniela-web Cloud Run service runs as."

  depends_on = [google_project_service.enabled]
}

# ─── api_runtime: connect to Cloud SQL + read DB password ───────────────────

resource "google_project_iam_member" "api_runtime_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.api_runtime.email}"
}

resource "google_secret_manager_secret_iam_member" "api_runtime_db_password" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.db_password.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api_runtime.email}"
}

# ─── web_runtime: read NextAuth + Google OAuth secrets ──────────────────────

resource "google_secret_manager_secret_iam_member" "web_runtime_nextauth" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.nextauth_secret.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.web_runtime.email}"
}

resource "google_secret_manager_secret_iam_member" "web_runtime_google_oauth" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.google_oauth_client_secret.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.web_runtime.email}"
}

# The API also needs nextauth-secret (to validate session tokens issued by
# the frontend), assuming we use a shared signing key. If we end up with the
# API issuing its own tokens after verifying Google's ID token, this can go.
resource "google_secret_manager_secret_iam_member" "api_runtime_nextauth" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.nextauth_secret.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api_runtime.email}"
}

# football-data.org API key — consumed by the API service at startup
# (FootballDataLoader) to seed teams + matches.
resource "google_secret_manager_secret_iam_member" "api_runtime_football_data" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.football_data_api_key.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api_runtime.email}"
}

# Vertex AI — Octopus Paul calls Gemini through aiplatform.googleapis.com using
# this SA's ADC (no API key). roles/aiplatform.user grants predict/generateContent.
# Replaces the former gemini-api-key secretAccessor binding (AI Studio path).
resource "google_project_iam_member" "api_runtime_vertex" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${google_service_account.api_runtime.email}"
}
