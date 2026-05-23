# Docker image registry. Replaces Heroku's registry.heroku.com.
#
# Final image URIs look like:
#   us-central1-docker.pkg.dev/<project>/apps/quiniela-api:latest
#   us-central1-docker.pkg.dev/<project>/apps/quiniela-web:latest

resource "google_artifact_registry_repository" "apps" {
  project       = var.project_id
  location      = var.region
  repository_id = var.artifact_registry_repo
  format        = "DOCKER"
  description   = "Quiniela 2026 app images (api + web)"

  # Auto-prune old image versions to keep storage costs negligible.
  # Keep the 5 most recent of each image; everything older gets deleted
  # after 30 days.
  cleanup_policies {
    id     = "keep-recent-5"
    action = "KEEP"
    most_recent_versions {
      keep_count = 5
    }
  }

  cleanup_policies {
    id     = "delete-old"
    action = "DELETE"
    condition {
      older_than = "2592000s" # 30 days
    }
  }

  depends_on = [google_project_service.enabled]
}
