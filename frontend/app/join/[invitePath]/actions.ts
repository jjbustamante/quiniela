"use server";

import { cookies } from "next/headers";

export async function rememberInvitePath(path: string) {
  const jar = await cookies();
  jar.set("invitePath", path, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    maxAge: 60 * 30, // 30 minutes
    path: "/",
  });
}
