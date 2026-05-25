import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");

const nextConfig: NextConfig = {
  // Tell Turbopack the workspace root explicitly. Without this, Next.js 16
  // tries to infer it from imports and fails in CNB builds — the buildpack
  // copies the app to /workspace and pnpm's symlinked node_modules confuses
  // the walk from app/ → next/package.json.
  //
  // `process.cwd()` is the directory `next build` was invoked from:
  //   local: <repo>/frontend (where next/package.json lives via node_modules)
  //   CNB:   /workspace      (same, via the buildpack's pnpm install)
  // Avoiding `import.meta.url` / `__dirname` here — without "type": "module"
  // in package.json those compile to CJS-shaped output that Next then loads
  // as ESM, throwing "exports is not defined in ES module scope".
  //
  // https://nextjs.org/docs/app/api-reference/config/next-config-js/turbopack#root-directory
  turbopack: {
    root: process.cwd(),
  },
};

export default withNextIntl(nextConfig);
