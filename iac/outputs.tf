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
