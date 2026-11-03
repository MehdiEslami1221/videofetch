# VideoFetch Studio

A responsive Persian / English Next.js interface for the VideoFetch Spring Boot backend, with two workflows: public preview of selected video platforms and direct upload of owned local files for MP3/MP4 processing.

## Stack

Next.js 16.4.4 · React 19.3.1 · TypeScript · Tailwind CSS 4.3.3 · Lucide icons. Dark/light themes, RTL/LTR support, persistent session job ID, responsive panels and progress polling.

## Run on Windows

Keep Spring Boot running on port 8080. In a second terminal, from **this frontend directory**:

```powershell
node -v  # >= 22
Copy-Item .env.example .env.local
npm install
npm run dev
```

Open `http://localhost:3000`. Verify `http://localhost:3000/api/health` returns JSON with `"status":"UP"`. Next.js rewrites `/api/...` to the backend at `BACKEND_URL` (defaults to `http://127.0.0.1:8080`); no public backend URL or client-side API key needed. Restart `npm run dev` after changing `.env.local`.

## Features

- Browser-only language and theme preferences (`localStorage`)
- Public video preview for YouTube / TikTok oEmbed with extractor fallback; Instagram extractor or explicit link-only fallback
- Actual extractable heights when known; otherwise options are labeled preferences, **not confirmed availability**
- Permission confirmation before starting an export
- A local file processing tab to test the full flow independent of platform restrictions
- Queue polling, progress indicator, error code, and download link only when `COMPLETED`
- Last job ID in `sessionStorage`; may expire or disappear after backend restart

## Build

```powershell
npm run check
npm run typecheck
npm run build
```

First `npm install` creates `package-lock.json`. Commit it with the source but do not commit `.env.local`, `.next/`, or `node_modules/`.

## Important limitations

A public video thumbnail is not proof a platform grants file downloads. VideoFetch honors source accessibility and user rights; it does not bypass authentication, bot challenges, DRM, or private-account restrictions. The backend is a local development showcase and requires authentication, rate limiting and storage isolation before public hosting. See the backend README for the complete list.

## Troubleshooting

- Backend indicator offline: run Spring Boot and test `localhost:3000/api/health`.
- "Tools unavailable": inspect backend `/api/system/tools` and IntelliJ Working directory.
- "Authentication required": the video platform rejected `yt-dlp`; do not repeatedly change the front-end.
- File upload processing: choose one of the listed types and confirm rights.
- Job disappears after backend restart: expected with in-memory storage.

A step-by-step extended setup guide is in `docs/GETTING_STARTED.md`.

## Reviewed release notes

Supports direct Instagram Story/Highlight links in addition to video posts/Reels. The preview may contain no title if the source does not expose public metadata; a thumbnail never proves file availability. A JavaScript runtime and proxy may be required on the backend. Status/errors in the UI distinguish authorization, rate limits and access denial.

When the user confirms they have rights to process content, the frontend now explicitly transmits that confirmation to the backend instead of silently sending `true` from the API helper. This UI does not implement automatic story lists for usernames or attempt to bypass restrictions on private accounts.
