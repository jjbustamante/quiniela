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
