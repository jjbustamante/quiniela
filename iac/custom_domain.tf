# Custom domain — Cloudflare DNS + Cloud Run domain mappings.
#
# Layout:
#   var.custom_domain               → web service  (apex of the zone)
#   "api.${var.custom_domain}"      → api service
#
# Flow:
#   1. google_cloud_run_domain_mapping creates the mapping. Cloud Run starts
#      provisioning a managed TLS cert AS SOON AS DNS resolves correctly.
#   2. cloudflare_dns_record adds the CNAME → ghs.googlehosted.com record
#      Cloud Run expects. Proxied=false is REQUIRED for cert provisioning;
#      Cloudflare proxy would intercept the ACME challenge.
#   3. Cert provisioning takes ~15-60 min on first apply. Subsequent renewals
#      are automatic and invisible.
#
# Cloudflare's CNAME flattening makes the apex-CNAME work despite RFC 1035
# normally forbidding CNAMEs at the zone apex.

# ─── Cloud Run side ─────────────────────────────────────────────────────────

resource "google_cloud_run_domain_mapping" "web" {
  project  = var.project_id
  location = var.region
  name     = var.custom_domain

  metadata {
    namespace = var.project_id
  }

  spec {
    route_name = google_cloud_run_v2_service.web.name
  }
}

resource "google_cloud_run_domain_mapping" "api" {
  project  = var.project_id
  location = var.region
  name     = "api.${var.custom_domain}"

  metadata {
    namespace = var.project_id
  }

  spec {
    route_name = google_cloud_run_v2_service.api.name
  }
}

# ─── Cloudflare DNS side ────────────────────────────────────────────────────

resource "cloudflare_dns_record" "web" {
  zone_id = var.cloudflare_zone_id
  name    = var.custom_domain
  type    = "CNAME"
  content = "ghs.googlehosted.com"
  proxied = false # MUST be false for Cloud Run managed cert provisioning
  ttl     = 1     # 1 = "auto" in Cloudflare; required when proxied=false
  comment = "Managed by OpenTofu — see iac/custom_domain.tf"
}

resource "cloudflare_dns_record" "api" {
  zone_id = var.cloudflare_zone_id
  name    = "api.${var.custom_domain}"
  type    = "CNAME"
  content = "ghs.googlehosted.com"
  proxied = false
  ttl     = 1
  comment = "Managed by OpenTofu — see iac/custom_domain.tf"
}
