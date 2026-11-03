import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "VideoFetch — Media workspace",
  description: "A bilingual workspace for media previews and permission-based processing.",
  applicationName: "VideoFetch",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="fa" dir="rtl" suppressHydrationWarning><body>{children}</body></html>;
}
