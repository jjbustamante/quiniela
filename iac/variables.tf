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
