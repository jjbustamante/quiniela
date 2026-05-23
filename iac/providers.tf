terraform {
  required_version = ">= 1.10.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }

  # Remote state — see bootstrap.sh for how the bucket gets created.
  # Bucket name follows the convention <project-id>-tofu-state. If you change
  # the project, update the bucket name here too (OpenTofu does not allow
  # variables in the backend block).
  backend "gcs" {
    bucket = "project-99f153c5-0682-4725-abd-tofu-state"
    prefix = "tofu/state"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
