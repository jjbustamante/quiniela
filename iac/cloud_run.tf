# Cloud Run services (api + web).
#
# Image lifecycle: declared with a placeholder image. The deploy script
# (`bin/deploy-*-gcp.sh`, future PR) pushes the real image and updates the
# service via `gcloud run deploy --image=...`. We mark `image` as
# `ignore_changes` so a subsequent `tofu apply` won't revert the deploy
# script's update.
#
# Tofu owns: env vars, secrets, runtime SA, Cloud SQL connection, scaling.
# Deploy script owns: image tag.

data "google_project" "current" {
  project_id = var.project_id
}

locals {
  # Cloud Run v2 services get URLs in the form
  #   https://<service>-<project-number>.<region>.run.app
  # We know the project number and region, so we can predict the web URL
  # before the service exists. This is needed because NEXTAUTH_URL must
  # equal the actual public URL of the web service (Auth.js validates it),
  # and a service can't reference its own .uri in an env var.
  web_url = "https://quiniela-web-${data.google_project.current.number}.${var.region}.run.app"

  placeholder_image = "us-docker.pkg.dev/cloudrun/container/hello"
}

# ─── Backend (quiniela-api) ─────────────────────────────────────────────────

resource "google_cloud_run_v2_service" "api" {
  project             = var.project_id
  name                = "quiniela-api"
  location            = var.region
  deletion_protection = false # services are easy to recreate; data has its own protection

  template {
    service_account = google_service_account.api_runtime.email

    scaling {
      min_instance_count = 0 # scale to zero when idle — free
      max_instance_count = var.cloud_run_max_instances
    }

    containers {
      image = local.placeholder_image # deploy script overrides

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = true # only bill CPU during requests
        startup_cpu_boost = true # faster Spring Boot cold start
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "cloudrun"
      }
      env {
        name  = "CLOUDSQL_CONNECTION_NAME"
        value = google_sql_database_instance.main.connection_name
      }
      env {
        name  = "DB_NAME"
        value = var.cloud_sql_database_name
      }
      env {
        name  = "DB_USER"
        value = var.cloud_sql_app_user
      }
      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "NEXTAUTH_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.nextauth_secret.secret_id
            version = "latest"
          }
        }
      }

      ports {
        container_port = 8080
      }
    }

    # Mount the Cloud SQL Auth Proxy as a sidecar — gives the container a
    # Unix socket at /cloudsql/<connection-name> for IAM-authenticated DB
    # connections. The api_runtime SA already has roles/cloudsql.client.
    volumes {
      name = "cloudsql"
      cloud_sql_instance {
        instances = [google_sql_database_instance.main.connection_name]
      }
    }
  }

  lifecycle {
    ignore_changes = [
      # Deploy script updates this — Tofu must not revert it.
      template[0].containers[0].image,
      # gcloud and the SDK set these on every deploy; ignore noise.
      client,
      client_version,
    ]
  }

  depends_on = [
    google_project_service.enabled,
    google_secret_manager_secret_version.db_password,
    google_secret_manager_secret_version.nextauth_secret,
  ]
}

# Allow unauthenticated invocations. Authn/authz happens at the app layer
# via Google ID token verification on incoming requests.
resource "google_cloud_run_v2_service_iam_member" "api_public" {
  project  = var.project_id
  location = google_cloud_run_v2_service.api.location
  name     = google_cloud_run_v2_service.api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# ─── Frontend (quiniela-web) ────────────────────────────────────────────────

resource "google_cloud_run_v2_service" "web" {
  project             = var.project_id
  name                = "quiniela-web"
  location            = var.region
  deletion_protection = false

  template {
    service_account = google_service_account.web_runtime.email

    scaling {
      min_instance_count = 0
      max_instance_count = var.cloud_run_max_instances
    }

    containers {
      image = local.placeholder_image

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      env {
        name  = "NODE_ENV"
        value = "production"
      }
      # Server-side fetch URL — points at the api Cloud Run service.
      env {
        name  = "API_URL"
        value = google_cloud_run_v2_service.api.uri
      }
      # Auth.js needs to know its own canonical URL for OAuth callbacks.
      env {
        name  = "NEXTAUTH_URL"
        value = local.web_url
      }
      env {
        name  = "AUTH_GOOGLE_ID"
        value = var.google_oauth_client_id
      }
      env {
        name = "AUTH_GOOGLE_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.google_oauth_client_secret.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "NEXTAUTH_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.nextauth_secret.secret_id
            version = "latest"
          }
        }
      }

      ports {
        container_port = 3000
      }
    }
  }

  lifecycle {
    ignore_changes = [
      template[0].containers[0].image,
      client,
      client_version,
    ]
  }

  depends_on = [
    google_project_service.enabled,
    google_secret_manager_secret_version.nextauth_secret,
    # google_oauth_client_secret has no Tofu-managed version (populated
    # manually via Console), so we depend on the secret resource itself.
    google_secret_manager_secret.google_oauth_client_secret,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "web_public" {
  project  = var.project_id
  location = google_cloud_run_v2_service.web.location
  name     = google_cloud_run_v2_service.web.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
