# iac/ — Infrastructure as Code (OpenTofu + GCP)

Provisions the GCP resources that host this project: Artifact Registry, Cloud Run, Cloud SQL, Secret Manager, IAM. State lives in a GCS bucket (NOT local) so the user and the friend stay in sync.

## What lives here today

- Enabled GCP APIs (Cloud Run, Cloud SQL, Artifact Registry, Secret Manager, IAM, ...)
- Artifact Registry Docker repo `apps` for our images
- **Cloud SQL Postgres** instance `quiniela-db` (db-f1-micro, ZONAL, deletion-protected) with database `quiniela` and app user `quiniela_app`
- **Secret Manager** secrets: `database-password` (auto-generated), `nextauth-secret` (auto-generated), `google-oauth-client-secret` (empty placeholder — populated manually after creating the OAuth client in Console), `football-data-api-key` (empty placeholder — populated manually after signing up at football-data.org)
- **Service accounts**:
  - `quiniela-deploy` — used by CI / `bin/deploy-*.sh` to push images and deploy Cloud Run services
  - `quiniela-api-runtime` — identity for the backend Cloud Run service; can connect to Cloud SQL and read DB password + NextAuth secret
  - `quiniela-web-runtime` — identity for the frontend Cloud Run service; can read NextAuth + Google OAuth secrets
- **Cloud Run services** `quiniela-api` and `quiniela-web` (declared with a placeholder `hello` image; deploy script overrides — see "Image lifecycle" below). Both are publicly invocable; authn/authz happens at the app layer. The api service has the Cloud SQL Auth Proxy mounted as a sidecar.
- **Custom domain** via Cloudflare DNS + Cloud Run domain mapping: `laquinieladelospanas.com` → web service only. DNS-only mode (no Cloudflare proxy) so Cloud Run can provision its own managed TLS cert. First-time cert provisioning takes ~15-60 min after `tofu apply`. The api service is intentionally not mapped to a custom domain — see `iac/custom_domain.tf` for the reasoning.

## Image lifecycle

Cloud Run services need an image at creation time. We declare them with `us-docker.pkg.dev/cloudrun/container/hello` as a placeholder, and mark `template[0].containers[0].image` as `ignore_changes` so:

1. First `tofu apply` creates the service running hello-world.
2. Deploy script (`bin/deploy-*-gcp.sh`, future PR) pushes the real image and `gcloud run deploy --image=...` updates the service.
3. Subsequent `tofu apply` runs don't touch the image — Tofu manages structure (env vars, secrets, scaling, runtime SA), deploy script manages releases.

## First-time setup

You need this once per GCP project. Skip if the state bucket already exists.

```bash
# 1. Authenticate as the project owner
gcloud auth login quiniela.panas.svp@gmail.com
gcloud config set project project-99f153c5-0682-4725-abd
gcloud auth application-default login   # for OpenTofu

# 2. Create the GCS state bucket
./bootstrap.sh

# 3. Create a Cloudflare API token (one-time, manual)
#    https://dash.cloudflare.com/profile/api-tokens
#    → "Edit zone DNS" template, scope to your zone (e.g. laquinieladelospanas.com)
#    Then either:
export TF_VAR_cloudflare_api_token="cf_token_..."
#    OR put it in terraform.tfvars (gitignored)

# 4. Get the Cloudflare zone ID — on the zone's Overview page in
#    the Cloudflare dashboard, right-hand side under "API"

# 5. Copy and fill in tfvars (terraform.tfvars is gitignored)
cp terraform.tfvars.example terraform.tfvars

# 4. Init OpenTofu against the remote backend
tofu init

# 5. Verify
tofu plan
tofu apply
```

## Day-to-day

```bash
tofu plan          # preview changes
tofu apply         # apply them
tofu state list    # see what's tracked
tofu output        # see exported values (registry URL, etc.)
```

## State storage

GCS bucket `quiniela-panas-tofu-state` in the project, with:

- **Object versioning enabled** — every state change is a new object generation; you can roll back
- **Uniform bucket-level access** — IAM-controlled, no per-object ACLs
- **Lock prevention via [GCS native locking](https://opentofu.org/docs/language/settings/backends/gcs/)** — concurrent `tofu apply`s won't corrupt state

The state file itself contains DB passwords and other secrets once we add them, so the bucket is private (only project members with `roles/storage.objectAdmin` can read it).

## File layout

```
iac/
├── README.md                   This file
├── bootstrap.sh                One-shot: create the state bucket
├── providers.tf                google provider + gcs backend
├── variables.tf                Typed input variables
├── terraform.tfvars.example    Template (committed); copy to terraform.tfvars
├── apis.tf                     Enabled GCP APIs
├── artifact_registry.tf        Docker image repo
├── service_accounts.tf         Deploy SA + IAM bindings
├── outputs.tf                  Values for deploy scripts to read
└── .gitignore                  Local state, .terraform/, real tfvars
```

## Secret Manager values to populate manually

Two secrets are declared by Tofu but their values are never written by Tofu (manually-rotated or externally-sourced):

- **`google-oauth-client-secret`** — created in Google Cloud Console (APIs & Services → Credentials → OAuth 2.0 Client). Paste the client secret with:
  ```bash
  echo -n "$YOUR_CLIENT_SECRET" | gcloud secrets versions add google-oauth-client-secret --data-file=-
  ```

- **`football-data-api-key`** — sign up at [football-data.org/client/register](https://www.football-data.org/client/register) (free tier, 10 req/min). The API key appears on your profile page. Paste it with:
  ```bash
  echo -n "$YOUR_API_KEY" | gcloud secrets versions add football-data-api-key --data-file=-
  ```
  Once populated, the backend `quiniela-api` Cloud Run service will fetch real teams + matches from football-data.org on its first startup (when the team table is empty). Set `APP_FOOTBALL_DATA_ENABLED=false` to disable loading without removing the secret.

## What's NOT in OpenTofu (yet)

- **The Google OAuth client** is managed in the Console (manual; was set up before IaC existed)
- **Cloud Run public URLs** — these are auto-assigned by GCP. `web_service_url` is a tfvar set manually after first apply (see variables.tf).

## Conventions

- One environment for now (no `dev`/`prod` workspaces). A friend pool doesn't need separation; we can add it later if useful.
- Resource names use `quiniela-` prefix where uniqueness matters.
- Region is `us-central1` (cheapest, broadest GCP service coverage). Override via `region` in tfvars if you'd rather use a Latin American region.
