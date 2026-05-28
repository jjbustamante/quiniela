# Cloud SQL — managed Postgres.
#
# Connection model: public IP enabled but no authorized_networks. Cloud Run
# reaches the DB via Cloud SQL Auth Proxy (Unix socket at
# /cloudsql/<project>:<region>:<instance>), authenticated via IAM. The
# backend's runtime service account gets roles/cloudsql.client to use it.
#
# No human user has direct network access — to psql from your laptop, use:
#   gcloud sql connect quiniela-db --user=quiniela_app

resource "google_sql_database_instance" "main" {
  project          = var.project_id
  name             = "quiniela-db"
  region           = var.region
  database_version = var.cloud_sql_database_version

  # Prevents `tofu destroy` from dropping the instance. Override by setting
  # this to false explicitly, applying, then destroying — two-step on purpose.
  deletion_protection = true

  settings {
    tier    = var.cloud_sql_tier
    edition = "ENTERPRISE" # NOT Enterprise Plus — that edition doesn't support
    # shared-core tiers (db-f1-micro, db-g1-small). Google
    # defaults new instances to Enterprise Plus, so this is
    # an explicit opt-in to the cheaper edition.
    availability_type = "ZONAL" # single-zone; REGIONAL doubles cost for HA we don't need
    disk_size         = 10      # GB; minimum for SSD
    disk_type         = "PD_SSD"
    disk_autoresize   = true

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
      start_time                     = "03:00" # UTC
      transaction_log_retention_days = 7
    }

    ip_configuration {
      ipv4_enabled = true # public IP, but no authorized_networks → proxy-only
      # ssl_mode default is ENCRYPTED_ONLY for Postgres 12+
    }

    # db-f1-micro defaults max_connections to ~25. Cloud Run rolling deploys
    # briefly overlap old + new revisions, and each revision's HikariCP pool
    # opens connections in parallel with Flyway's startup probe. With the
    # JDBC defaults that easily exceeds 25 and the new revision dies with
    # `FATAL: remaining connection slots are reserved`. Pair with the
    # HikariCP cap in backend application-cloudrun.yml.
    database_flags {
      name  = "max_connections"
      value = "50"
    }

    maintenance_window {
      day          = 7 # Sunday
      hour         = 3 # 03:00 UTC = ~22:00 Caracas — late evening, low quiniela traffic
      update_track = "stable"
    }

    insights_config {
      query_insights_enabled  = true
      query_string_length     = 1024
      record_application_tags = false
      record_client_address   = false
    }
  }

  depends_on = [google_project_service.enabled]
}

resource "google_sql_database" "app" {
  project  = var.project_id
  name     = var.cloud_sql_database_name
  instance = google_sql_database_instance.main.name
}

# App user — Spring Boot connects as this. Password lives in Secret Manager
# (see secrets.tf); the DB itself only stores a hash.
resource "google_sql_user" "app" {
  project  = var.project_id
  name     = var.cloud_sql_app_user
  instance = google_sql_database_instance.main.name
  password = random_password.db_app_user.result
}

resource "random_password" "db_app_user" {
  length  = 32
  special = true
  # Restrict special chars to URL-safe ones so the password can appear in
  # a postgres:// URL without escaping. Excludes characters that have
  # meaning in URIs (`/`, `?`, `#`, `@`, `&`, `:`, `=`, `+`, etc.).
  override_special = "_-.~"
}
