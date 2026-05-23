variable "project_id" {
  description = "GCP project ID that owns all resources for the quiniela app."
  type        = string
}

variable "region" {
  description = "Default GCP region for regional resources (Cloud Run, Artifact Registry, Cloud SQL)."
  type        = string
  default     = "us-central1"
}

variable "owner_email" {
  description = "Email of the human who owns the project — used for IAM bindings, budget alert recipients, etc."
  type        = string
}

variable "artifact_registry_repo" {
  description = "Name of the Artifact Registry Docker repository that holds our images. Becomes `<region>-docker.pkg.dev/<project>/<repo>/<image>`."
  type        = string
  default     = "apps"
}

variable "cloud_sql_tier" {
  description = "Cloud SQL machine type. db-f1-micro = shared CPU, 0.6GB RAM, ~$9/mo. Bump to db-g1-small or db-custom-N-M for more."
  type        = string
  default     = "db-f1-micro"
}

variable "cloud_sql_database_version" {
  description = "Postgres major version. Supported: POSTGRES_14, POSTGRES_15, POSTGRES_16, POSTGRES_17."
  type        = string
  default     = "POSTGRES_16"
}

variable "cloud_sql_database_name" {
  description = "Logical database name inside the Cloud SQL instance."
  type        = string
  default     = "quiniela"
}

variable "cloud_sql_app_user" {
  description = "Postgres role used by the backend app (Spring Boot connects as this)."
  type        = string
  default     = "quiniela_app"
}

variable "google_oauth_client_id" {
  description = "Google OAuth 2.0 Client ID (the .apps.googleusercontent.com value). Not a secret — ends up in browser-readable Next.js config."
  type        = string
}

variable "cloud_run_max_instances" {
  description = "Max Cloud Run instances per service. Friend pool needs ~1; 5 is breathing room without risk of runaway billing."
  type        = number
  default     = 5
}

variable "web_service_url" {
  description = <<-EOT
    Public URL of the web Cloud Run service. Auth.js needs this for OAuth
    callbacks. Set AFTER first apply when you know the actual URL (run
    `tofu output web_service_url` after the service exists), add it to
    terraform.tfvars, then re-run `tofu apply`. Leave empty on first apply.

    Why a manual step: Cloud Run URLs follow one of two unpredictable
    patterns depending on project age, AND a Cloud Run service can't
    reference its own .uri inside its own env vars.
  EOT
  type        = string
  default     = ""
}
