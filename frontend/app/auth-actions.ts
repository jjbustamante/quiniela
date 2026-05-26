"use server";

import { signOut } from "@/lib/auth";

/** Single-use server action for the UserMenu's logout button. */
export async function signOutAction() {
  await signOut({ redirectTo: "/" });
}
