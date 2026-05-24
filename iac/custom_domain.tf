# Custom domain — Cloudflare DNS + Cloud Run domain mapping.
#
# Layout:
#   var.custom_domain  → web service  (apex of the zone)
#
# The api service is intentionally NOT mapped to a custom domain. It's only
# called server-side by the web service (which uses the .run.app URL via the
# API_URL env var), so a friendly hostname adds no user value. We previously
# tried `api.${var.custom_domain}` but dpdns.org's nameservers are
# inconsistent on subdomain delegation — some serve the Cloudflare NS
# referral, others return NXDOMAIN — which broke Cloud Run's ACME validator.
# Not worth fighting; api stays on .run.app.
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

resource "cloudflare_dns_record" "google_site_verification" {
  zone_id = var.cloudflare_zone_id
  name    = var.custom_domain
  type    = "TXT"
  content = "\"google-site-verification=UJfUU_SgkRFf-uF8o1PGmr5BnIS0skBnjC777HoGXl0\"" # quotes are part of TXT format
  ttl     = 1
}


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

