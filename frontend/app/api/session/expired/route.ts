import { signOut } from "@/lib/auth";

// Route handler (not RSC) so it can mutate cookies — invoked when api() gets
// a 401 with a session present (user row deleted, JWT secret rotated, etc.).
export async function GET() {
  await signOut({ redirectTo: "/" });
}
