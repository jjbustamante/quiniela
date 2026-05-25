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

locals {
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
        # Bumped to 1 GiB after adding Spring Security + OAuth2 RS + JJWT.
        # Spring Boot 4 JRE container needs ~600-700 MB for Metaspace +
        # CodeCache + heap floor; 512 MiB made Paketo's memory-calculator
        # compute a negative max-heap → "failed to launch: memory-calculator".
        # See backend/project.toml `BP_JVM_THREAD_COUNT=50` which also helps.
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = true # only bill CPU during requests
        startup_cpu_boost = true # faster Spring Boot cold start
      }

      # CNB launcher (image ENTRYPOINT) needs this to know which platform
      # API contract to use. Cloud Run injects env vars into ENTRYPOINT just
      # fine (unlike Heroku Cedar container), so a config var works — no
      # wrapper Dockerfile needed here. 0.15 is the highest version the
      # heroku/builder:26 lifecycle supports.
      env {
        name  = "CNB_PLATFORM_API"
        value = "0.15"
      }
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "cloudrun"
      }
      # AUTH_GOOGLE_ID is the Google OAuth client ID; the api needs it to
      # verify inbound ID tokens (validates the `aud` claim).
      env {
        name  = "AUTH_GOOGLE_ID"
        value = var.google_oauth_client_id
      }
      # Comma-separated list of emails that get is_admin=true on first
      # sign-in. Defaults to the project owner.
      env {
        name  = "ADMIN_EMAILS"
        value = var.owner_email
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
      env {
        name  = "APP_FOOTBALL_DATA_ENABLED"
        value = "true"
      }
      env {
        name = "APP_FOOTBALL_DATA_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.football_data_api_key.secret_id
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
    # football_data_api_key has no Tofu-managed version (populated
    # manually via gcloud), so we depend on the secret resource itself.
    google_secret_manager_secret.football_data_api_key,
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

      # Same CNB launcher requirement as the api service. See comment there.
      env {
        name  = "CNB_PLATFORM_API"
        value = "0.15"
      }
      env {
        name  = "NODE_ENV"
        value = "production"
      }
      # Auth.js v5 behind Cloud Run's edge proxy: trust X-Forwarded-Host so
      # OAuth callbacks resolve to the public URL, not the internal one.
      env {
        name  = "AUTH_TRUST_HOST"
        value = "true"
      }
      # Server-side fetch URL — points at the api Cloud Run service.
      env {
        name  = "API_URL"
        value = google_cloud_run_v2_service.api.uri
      }
      # Auth.js needs its own canonical URL for OAuth callbacks. Conditional
      # because we only know the actual URL after first apply — see the
      # web_service_url variable for the workflow.
      dynamic "env" {
        for_each = var.web_service_url != "" ? [1] : []
        content {
          name  = "NEXTAUTH_URL"
          value = var.web_service_url
        }
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
