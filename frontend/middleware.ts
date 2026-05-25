import createMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

export default createMiddleware(routing);

export const config = {
  // Match everything except API, auth, static, image-opt, public assets.
  matcher: ["/((?!api|_next|.*\\..*).*)"],
};
