# Cloud Scheduler — daily sync job.
#
# Fires once a day at 11:00 UTC (06:00 America/Bogota) and POSTs to
# /internal/sync/daily, which refreshes fixtures and enqueues one Cloud Task
# per upcoming match (timed to kickoff + 105 min).
#
# Auth: shared-secret header (X-Sync-Token). The Cloud Run service is
# publicly invokable (allUsers) so IAM can't gate this route — the
# secret is checked in-app by SyncTokenFilter.

resource "google_cloud_scheduler_job" "daily_plan" {
  name      = "quiniela-daily-plan"
  project   = var.project_id
  region    = var.region
  schedule  = "0 11 * * *" # 06:00 America/Bogota
  time_zone = "Etc/UTC"

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.api.uri}/internal/sync/daily"
    headers = {
      "X-Sync-Token" = random_password.sync_token.result
    }
  }

  retry_config {
    retry_count = 1
  }

  depends_on = [google_project_service.enabled]
}
