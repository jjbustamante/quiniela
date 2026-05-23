# iac/ — Infrastructure as Code (OpenTofu + GCP)

Provisions the GCP resources that host this project: Artifact Registry, Cloud Run, Cloud SQL, Secret Manager, IAM. State lives in a GCS bucket (NOT local) so the user and the friend stay in sync.

## What lives here today

Just the foundation — APIs enabled, Artifact Registry repo for our images, and a deploy service account. Cloud SQL, Secret Manager, and Cloud Run services come in subsequent PRs.

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
