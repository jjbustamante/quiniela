# Workload Identity Federation — lets GitHub Actions impersonate the deploy
# SA without long-lived JSON service-account keys.
#
# Flow:
#   1. GH Actions workflow requests an OIDC token from GitHub
#      (https://token.actions.githubusercontent.com).
#   2. google-github-actions/auth presents that token to GCP STS.
#   3. STS validates against the provider below (attribute_condition pins it
#      to var.github_repository so other repos' tokens bounce).
#   4. STS returns a short-lived GCP access token scoped to the deploy SA.
#   5. gcloud / docker / pack use that token. No secrets, no keys, no rotation.

resource "google_iam_workload_identity_pool" "github" {
  project                   = var.project_id
  workload_identity_pool_id = "github-actions"
  display_name              = "GitHub Actions"
  description               = "Identity pool for GitHub Actions OIDC tokens (deploy SA impersonation)."

  depends_on = [google_project_service.enabled]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub Actions provider"

  # Maps GitHub OIDC claims onto GCP identity attributes. attribute.repository
  # is the one we condition on below.
  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.actor"      = "assertion.actor"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # Only OIDC tokens claiming this repository can use the pool. Anyone else's
  # GitHub Actions token (other repos, forks, etc.) bounces here — even if
  # they somehow guess the WIF_PROVIDER + WIF_SA values.
  attribute_condition = "assertion.repository == '${var.github_repository}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# Allow the pool's identities (restricted by attribute_condition above) to
# impersonate the deploy SA via roles/iam.workloadIdentityUser.
resource "google_service_account_iam_member" "deploy_wif_user" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}
