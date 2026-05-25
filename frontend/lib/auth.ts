import NextAuth from "next-auth";
import Google from "next-auth/providers/google";
import { cookies } from "next/headers";

const apiUrl = process.env.API_URL ?? "http://localhost:8080";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [Google],
  // Surface backend rejections on a friendly Spanish page instead of the
  // default Auth.js generic error screen. The error type is appended as
  // ?error=<code> by Auth.js — we read it server-side to pick a message.
  pages: {
    error: "/auth-error",
  },
  callbacks: {
    async jwt({ token, account }) {
      if (account?.id_token) {
        // Read (and clear) the invite path captured by /join/[invitePath].
        const jar = await cookies();
        const invitePath = jar.get("invitePath")?.value ?? null;
        if (invitePath) jar.delete("invitePath");

        let res: Response;
        try {
          res = await fetch(`${apiUrl}/auth/google`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ idToken: account.id_token, invitePath }),
          });
        } catch (err) {
          console.error("Backend /auth/google call failed:", err);
          // Network / DNS / Cloud SQL issue — bubble up as an error so the
          // user lands on /auth-error?error=BackendUnreachable instead of
          // a silent sign-out.
          throw new Error("BackendUnreachable");
        }
        if (!res.ok) {
          const status = res.status;
          console.error("Backend /auth/google rejected:", status, await res.text());
          // 403 = stranger with no invite OR a path that no longer resolves.
          // Anything else (5xx) = backend trouble; treat as transient.
          throw new Error(status === 403 ? "NoInvite" : "BackendError");
        }
        const data = (await res.json()) as {
          token: string;
          userId: number;
          role: "ADMIN" | "CAPTAIN" | "PLAYER";
          invitePath: string | null;
        };
        token.backendToken = data.token;
        token.userId = data.userId;
        token.role = data.role;
        token.invitePath = data.invitePath;
      }
      return token;
    },
    async session({ session, token }) {
      return {
        ...session,
        backendToken: token.backendToken,
        userId: token.userId,
        role: token.role,
        invitePath: token.invitePath,
      };
    },
  },
});
