# Cloud Tasks queue for per-match result checks.
#
# The results-sync queue receives one task per upcoming match (scheduled at
# kickoff + 105 min). Each task POSTs to /internal/sync/results?matchId=N and
# re-enqueues itself every 15 min until the match is FINISHED or a 5-hour
# cap expires. Rate limits are conservative — this is a handful of tasks/day.

resource "google_cloud_tasks_queue" "results_sync" {
  name     = "results-sync"
  location = var.region
  project  = var.project_id

  rate_limits {
    max_dispatches_per_second = 1
    max_concurrent_dispatches = 2
  }

  retry_config {
    max_attempts  = 5
    min_backoff   = "30s"
    max_backoff   = "300s"
    max_doublings = 2
  }

  depends_on = [google_project_service.enabled]
}
