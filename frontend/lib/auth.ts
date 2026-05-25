import NextAuth from "next-auth";
import Google from "next-auth/providers/google";

/**
 * Auth.js v5 configuration.
 *
 * Flow:
 *  1. Google OIDC handles the user-facing sign-in (button → consent → callback).
 *  2. In the `jwt` callback (server-side, first sign-in only), we POST the
 *     Google-issued ID token to our Spring backend at /auth/google.
 *  3. The backend verifies it against Google's JWKS, upserts the User row,
 *     and returns its own session JWT (signed with NEXTAUTH_SECRET so we can
 *     use the same value end-to-end).
 *  4. We stash that backend JWT inside Auth.js's session token. The frontend
 *     attaches it to outgoing /api/** calls as `Authorization: Bearer ...`.
 *
 * Env vars used (all set on Cloud Run via IaC):
 *   AUTH_GOOGLE_ID / AUTH_GOOGLE_SECRET — Google OAuth credentials
 *   AUTH_SECRET (or NEXTAUTH_SECRET)   — signing key for Auth.js session cookie
 *   AUTH_TRUST_HOST=true               — trust X-Forwarded-Host (needed behind Cloud Run)
 *   API_URL                            — backend base URL for the /auth/google call
 */

const apiUrl = process.env.API_URL ?? "http://localhost:8080";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [Google],
  callbacks: {
    async jwt({ token, account }) {
      // First sign-in only: account is present and carries the Google id_token.
      if (account?.id_token) {
        try {
          const res = await fetch(`${apiUrl}/auth/google`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ idToken: account.id_token }),
          });
          if (res.ok) {
            const data = (await res.json()) as {
              token: string;
              userId: number;
              admin: boolean;
            };
            token.backendToken = data.token;
            token.userId = data.userId;
            token.admin = data.admin;
          } else {
            console.error("Backend /auth/google rejected token:", res.status, await res.text());
          }
        } catch (err) {
          console.error("Backend /auth/google call failed:", err);
        }
      }
      return token;
    },
    async session({ session, token }) {
      // Return a fresh object instead of mutating — Auth.js v5's session
      // callback writable view is narrower than our augmented Session type
      // for per-field writes, but a spread + additions resolves cleanly.
      return {
        ...session,
        backendToken: token.backendToken,
        userId: token.userId,
        admin: token.admin,
      };
    },
  },
});
