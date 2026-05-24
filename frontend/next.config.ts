import type { NextConfig } from "next";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const nextConfig: NextConfig = {
  // Tell Turbopack the workspace root explicitly. Without this, Next.js 16
  // tries to infer it from imports and fails in CNB builds — the buildpack
  // copies the app to /workspace and pnpm's symlinked node_modules confuses
  // the walk from app/ → next/package.json.
  // https://nextjs.org/docs/app/api-reference/config/next-config-js/turbopack#root-directory
  turbopack: {
    root: __dirname,
  },
};

export default nextConfig;
