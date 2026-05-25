import "next-auth";

declare module "next-auth" {
  interface Session {
    backendToken?: string;
    userId?: number;
    role?: "ADMIN" | "CAPTAIN" | "PLAYER";
    invitePath?: string | null;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    backendToken?: string;
    userId?: number;
    role?: "ADMIN" | "CAPTAIN" | "PLAYER";
    invitePath?: string | null;
  }
}
