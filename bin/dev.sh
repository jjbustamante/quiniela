#!/usr/bin/env bash
#
# Local dev stack runner. Starts Postgres + backend (Spring Boot) +
# frontend (Next.js) in this terminal, streaming logs with a colored
# prefix per service.
#
# Ctrl+C stops backend + frontend. Postgres stays running so DB data
# persists between sessions; use `bin/dev.sh --stop` to halt it.
#
# Usage:
#   bin/dev.sh           Start everything (default).
#   bin/dev.sh --stop    Stop Postgres (after Ctrl+C).
#   bin/dev.sh --reset   Stop Postgres + wipe its data volume.
#   bin/dev.sh --help

set -euo pipefail
set -m   # job control: each background `&` gets its own process group,
         # so we can kill descendants (java, node, next, etc.) on exit.

cd "$(git rev-parse --show-toplevel)"

# ─── Subcommands ─────────────────────────────────────────────────────────

case "${1:-}" in
  --stop)
    echo "==> Stopping Postgres"
    (cd backend && docker compose down)
    exit 0
    ;;
  --reset)
    echo "==> Stopping Postgres + wiping data volume"
    (cd backend && docker compose down -v)
    exit 0
    ;;
  -h|--help)
    sed -n '3,17p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
esac

# ─── Sanity checks ───────────────────────────────────────────────────────

command -v docker >/dev/null || { echo "ERROR: docker not found"; exit 1; }
command -v pnpm   >/dev/null || { echo "ERROR: pnpm not found. Run: corepack enable && corepack prepare pnpm@10.27.0 --activate"; exit 1; }
[ -x backend/mvnw ] || { echo "ERROR: backend/mvnw not executable"; exit 1; }

# ─── Postgres (idempotent — won't restart if already up) ─────────────────

echo "==> Starting Postgres (docker compose up -d)"
(cd backend && docker compose up -d)

# ─── Cleanup on exit ─────────────────────────────────────────────────────
# kill -- -<pid> targets the entire process group. With `set -m` above,
# each `&` job is its own pgrp, so this catches mvnw's java child and
# pnpm dev's next child.

cleanup() {
  echo
  echo "==> Stopping backend + frontend"
  for pid in $(jobs -p); do
    kill -TERM -- "-${pid}" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  echo "==> Postgres still running. \`bin/dev.sh --stop\` to halt it."
}
trap cleanup INT TERM EXIT

# ─── Backend ─────────────────────────────────────────────────────────────
# Prefix every line with a blue [backend] tag. Watch your IDE's Spring
# Boot console output instead if you prefer; this script is opinionated
# about visibility.

(
  cd backend
  ./mvnw -q spring-boot:run 2>&1 \
    | sed -u "s/^/$(printf '\033[34m[backend]\033[0m ')/"
) &

# ─── Frontend ────────────────────────────────────────────────────────────

(
  cd frontend
  pnpm dev 2>&1 \
    | sed -u "s/^/$(printf '\033[35m[frontend]\033[0m ')/"
) &

# ─── Wait ────────────────────────────────────────────────────────────────

cat <<EOF

==> Up. Tail below. Ctrl+C to stop.
    Backend:  http://localhost:8080/actuator/health
    Frontend: http://localhost:3000

EOF

wait
