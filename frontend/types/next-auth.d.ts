// Augments Auth.js types so our custom session fields (backend JWT + flags) are
// typed end-to-end. See lib/auth.ts for where these get populated.

import type { DefaultSession } from "next-auth";

declare module "next-auth" {
  interface Session {
    /** The Spring-issued session JWT. Sent as Bearer to /api/** calls. */
    backendToken?: string;
    /** Numeric user ID assigned by the backend on first sign-in. */
    userId?: number;
    /** Whether this user is in ADMIN_EMAILS on the backend. */
    admin?: boolean;
    user?: DefaultSession["user"];
  }
}

// Auth.js v5 puts the JWT shape in @auth/core/jwt; next-auth/jwt re-exports it.
// Augmenting both keeps any future import path working.
declare module "@auth/core/jwt" {
  interface JWT {
    backendToken?: string;
    userId?: number;
    admin?: boolean;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    backendToken?: string;
    userId?: number;
    admin?: boolean;
  }
}
