# iac/ — Infrastructure as Code (OpenTofu + GCP)

Provisions the GCP resources that host this project: Artifact Registry, Cloud Run, Cloud SQL, Secret Manager, IAM. State lives in a GCS bucket (NOT local) so the user and the friend stay in sync.

## What lives here today

- Enabled GCP APIs (Cloud Run, Cloud SQL, Artifact Registry, Secret Manager, IAM, ...)
- Artifact Registry Docker repo `apps` for our images
- **Cloud SQL Postgres** instance `quiniela-db` (db-f1-micro, ZONAL, deletion-protected) with database `quiniela` and app user `quiniela_app`
- **Secret Manager** secrets: `database-password` (auto-generated), `nextauth-secret` (auto-generated), `google-oauth-client-secret` (empty placeholder — populated manually after creating the OAuth client in Console)
- **Service accounts**:
  - `quiniela-deploy` — used by CI / `bin/deploy-*.sh` to push images and deploy Cloud Run services
  - `quiniela-api-runtime` — identity for the backend Cloud Run service; can connect to Cloud SQL and read DB password + NextAuth secret
  - `quiniela-web-runtime` — identity for the frontend Cloud Run service; can read NextAuth + Google OAuth secrets
- **Cloud Run services** `quiniela-api` and `quiniela-web` (declared with a placeholder `hello` image; deploy script overrides — see "Image lifecycle" below). Both are publicly invocable; authn/authz happens at the app layer. The api service has the Cloud SQL Auth Proxy mounted as a sidecar.

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

# 3. Copy and fill in tfvars (terraform.tfvars is gitignored)
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

## What's NOT in OpenTofu (yet)

- **The Google OAuth client** is managed in the Console (manual; was set up before IaC existed)
- **Heroku resources** — we're migrating away; nothing here is Heroku-aware

## Conventions

- One environment for now (no `dev`/`prod` workspaces). A friend pool doesn't need separation; we can add it later if useful.
- Resource names use `quiniela-` prefix where uniqueness matters.
- Region is `us-central1` (cheapest, broadest GCP service coverage). Override via `region` in tfvars if you'd rather use a Latin American region.
