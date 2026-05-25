import NextAuth from "next-auth";
import Google from "next-auth/providers/google";
import { cookies } from "next/headers";

const apiUrl = process.env.API_URL ?? "http://localhost:8080";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [Google],
  callbacks: {
    async jwt({ token, account }) {
      if (account?.id_token) {
        // Read (and clear) the invite path captured by /join/[invitePath].
        const jar = await cookies();
        const invitePath = jar.get("invitePath")?.value ?? null;
        if (invitePath) jar.delete("invitePath");

        try {
          const res = await fetch(`${apiUrl}/auth/google`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ idToken: account.id_token, invitePath }),
          });
          if (res.ok) {
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
          } else {
            console.error("Backend /auth/google rejected:", res.status, await res.text());
          }
        } catch (err) {
          console.error("Backend /auth/google call failed:", err);
        }
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
