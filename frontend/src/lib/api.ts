export type Platform = "YOUTUBE" | "INSTAGRAM" | "TIKTOK";
export type FileFormat = "MP3" | "MP4";
export type JobStatus = "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
export interface Preview {
  platform: Platform;
  id: string;
  title: string;
  uploader: string;
  durationSeconds: number | null;
  thumbnailUrl: string;
  originalUrl: string;
  availableHeights: number[];
  metadataSource: "OEMBED" | "EXTRACTOR" | "LINK_ONLY";
  downloadAvailability: "UNKNOWN";
}
export interface Tools {
  ytDlpAvailable: boolean;
  ffmpegAvailable: boolean;
  ffprobeAvailable: boolean;
  ready: boolean;
  localConversionReady: boolean;
  proxyConfigured?: boolean;
  jsRuntimeAvailable?: boolean;
}
export interface Job {
  id: string;
  status: JobStatus;
  format: FileFormat;
  progress: number;
  fileName: string | null;
  sizeBytes: number | null;
  errorCode: string | null;
  error: string | null;
  createdAt: string;
  expiresAt: string | null;
  fileUrl: string | null;
  source: "REMOTE" | "UPLOAD";
}
export class ApiError extends Error {
  constructor(public code: string, message: string) { super(message); }
}
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, { cache: "no-store", ...options });
  } catch {
    throw new ApiError("OFFLINE", "Unable to reach the backend service.");
  }
  const type = response.headers.get("content-type") || "";
  const result: unknown = type.includes("application/json") ? await response.json().catch(() => ({})) : {};
  if (!response.ok) {
    const body = result as { code?: string; message?: string };
    throw new ApiError(body.code || "HTTP_ERROR", body.message || `Request failed (${response.status})`);
  }
  return result as T;
}
export const api = {
  health: () => request<{ status: string }>("/health"),
  tools: () => request<Tools>("/system/tools"),
  info: (url: string) => request<Preview>("/videos/info", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ url }),
  }),
  remote: (url: string, format: FileFormat, maxHeight: number, rightsConfirmed: boolean) => request<Job>("/downloads", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ url, format, maxHeight, rightsConfirmed }),
  }),
  upload: (file: File, format: FileFormat, maxHeight: number, rightsConfirmed: boolean) => {
    const data = new FormData();
    data.append("file", file);
    data.append("format", format);
    data.append("maxHeight", String(maxHeight));
    data.append("rightsConfirmed", String(rightsConfirmed));
    return request<Job>("/uploads", { method: "POST", body: data });
  },
  job: (id: string) => request<Job>(`/downloads/${encodeURIComponent(id)}`),
};
export function detectPlatform(raw: string): Platform | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:") return null;
    const host = url.hostname.toLowerCase();
    if (["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be"].includes(host)) return "YOUTUBE";
    if (["instagram.com", "www.instagram.com"].includes(host)) return /^\/(p|reel|tv|stories)\//.test(url.pathname) ? "INSTAGRAM" : null;
    if (["tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com"].includes(host)) return "TIKTOK";
    return null;
  } catch { return null; }
}
export const formatBytes = (n: number) => `${(n / 1024 / 1024).toFixed(1)} MB`;
