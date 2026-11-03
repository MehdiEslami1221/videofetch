import type { NextConfig } from "next";

const backend = process.env.BACKEND_URL || "http://127.0.0.1:8080";
// Avoid turning rewrites into an arbitrary external proxy.
if (!/^https?:\/\/(127\.0\.0\.1|localhost)(:\d+)?\/?$/.test(backend)) {
  throw new Error("BACKEND_URL must point to a local backend in this development build.");
}
const config: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend.replace(/\/$/, "")}/api/:path*` }];
  },
};
export default config;
