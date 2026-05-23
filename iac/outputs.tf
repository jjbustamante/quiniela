# Outputs are how the deploy scripts (and you on the CLI) read values that
# OpenTofu computed. See them with: `tofu output` or `tofu output -json`.

output "project_id" {
  description = "GCP project ID."
  value       = var.project_id
}

output "region" {
  description = "Default region for regional resources."
  value       = var.region
}

output "artifact_registry_host" {
  description = "Docker registry hostname for `docker login` / `docker tag`."
  value       = "${var.region}-docker.pkg.dev"
}

output "artifact_registry_repo_url" {
  description = "Full repo URL — prepend this to an image name and tag, e.g. <repo>/quiniela-api:latest."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.apps.repository_id}"
}

output "deploy_service_account_email" {
  description = "Email of the deploy service account. Use with `gcloud iam service-accounts keys create` or Workload Identity Federation for CI."
  value       = google_service_account.deploy.email
}

# ─── Cloud SQL ──────────────────────────────────────────────────────────────

output "cloud_sql_instance_name" {
  description = "Short Cloud SQL instance name. Use with `gcloud sql connect`."
  value       = google_sql_database_instance.main.name
}

output "cloud_sql_connection_name" {
  description = "Full instance connection name in <project>:<region>:<instance> form. Used for Cloud Run --add-cloudsql-instances and the proxy socket path."
  value       = google_sql_database_instance.main.connection_name
}

output "cloud_sql_database_name" {
  description = "Logical database the app connects to."
  value       = google_sql_database.app.name
}

output "cloud_sql_app_user" {
  description = "Postgres role used by the backend app."
  value       = google_sql_user.app.name
}

# ─── Runtime service accounts ───────────────────────────────────────────────

output "api_runtime_service_account_email" {
  description = "Identity the quiniela-api Cloud Run service runs as."
  value       = google_service_account.api_runtime.email
}

output "web_runtime_service_account_email" {
  description = "Identity the quiniela-web Cloud Run service runs as."
  value       = google_service_account.web_runtime.email
}

# ─── Secret IDs (NOT values) ────────────────────────────────────────────────
# Names only — actual values stay in Secret Manager. Use these with
# `gcloud run deploy --set-secrets KEY=<id>:latest`.

output "secret_id_database_password" {
  description = "Secret Manager ID for the Postgres app-user password."
  value       = google_secret_manager_secret.db_password.secret_id
}

output "secret_id_nextauth_secret" {
  description = "Secret Manager ID for the Auth.js session signing secret."
  value       = google_secret_manager_secret.nextauth_secret.secret_id
}

output "secret_id_google_oauth_client_secret" {
  description = "Secret Manager ID for the Google OAuth client secret (manually populated via Console after creating the OAuth client)."
  value       = google_secret_manager_secret.google_oauth_client_secret.secret_id
}

# ─── Cloud Run services ─────────────────────────────────────────────────────

output "api_service_url" {
  description = "Public URL of the backend Cloud Run service. Use as API_URL on the web service (already wired via Tofu)."
  value       = google_cloud_run_v2_service.api.uri
}

output "web_service_url" {
  description = "Public URL of the frontend Cloud Run service. Add this + '/api/auth/callback/google' as an Authorized Redirect URI in the Google OAuth client (Console → APIs & Services → Credentials)."
  value       = google_cloud_run_v2_service.web.uri
}

output "api_service_name" {
  description = "Cloud Run service name for the backend — used by deploy script."
  value       = google_cloud_run_v2_service.api.name
}

output "web_service_name" {
  description = "Cloud Run service name for the frontend — used by deploy script."
  value       = google_cloud_run_v2_service.web.name
}
