"use server";

import { cookies } from "next/headers";
import { signIn } from "@/lib/auth";

/**
 * Server action invoked by the /join sign-in form. Sets the invitePath cookie
 * (which lib/auth.ts reads inside the Auth.js jwt callback) and then triggers
 * the Google OIDC flow. Cookie writes are only legal inside server actions
 * and route handlers — that's why this can't happen during page render.
 */
export async function signInWithInvite(formData: FormData) {
  const path = formData.get("invitePath")?.toString();
  if (path) {
    const jar = await cookies();
    jar.set("invitePath", path, {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      maxAge: 60 * 30,
      path: "/",
    });
  }
  await signIn("google", { redirectTo: "/home" });
}
