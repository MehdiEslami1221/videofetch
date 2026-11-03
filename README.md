# VideoFetch

A bilingual media preview and conversion application built with **Spring Boot** and **Next.js**. VideoFetch separates public link previews from actual media access, supports queued MP3/MP4 processing, and includes a local-file workflow for reliable end-to-end testing.

## Tech stack

| Service | Stack |
| --- | --- |
| API | Java 21, Spring Boot 4.1.2, Maven |
| Web | Next.js 16, React 19, TypeScript, Tailwind CSS 4 |
| Media processing | yt-dlp, FFmpeg / ffprobe |
| Integration | REST API, background jobs, status polling |
| Localization | English (LTR) and Persian (RTL) |

The frontend and backend are maintained in the same repository, but can be developed independently. No Docker or database is required for the local demo.

## What it does

- Accepts supported YouTube, TikTok, and Instagram media URLs, including direct Instagram Story/Highlight links.
- Displays preview metadata when the source provides it; an available preview does **not** guarantee that a media file is downloadable.
- Submits permitted remote-media jobs and reports real processing states, progress and structured failures.
- Converts files uploaded by the user to MP3 or MP4 without reliance on third-party sites.
- Provides an English/Persian responsive UI with light/dark themes.
- Includes a Postman collection, Windows installer script for media tools and CI checks.

Instagram Story discovery from a username alone is not implemented. External platform authentication and access restrictions are not bypassed.

## Getting started

### 1. Run the backend

Prerequisites: **JDK 21**, Windows PowerShell, and **Node.js 22+** (used by both the frontend and some media extractors). Open `backend/` in IntelliJ IDEA with its working directory set to the folder containing `pom.xml`.

```powershell
cd backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-tools.ps1
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

API check: `http://localhost:8080/api/health`

If your network needs a local HTTP proxy, set an environment variable in your IntelliJ run configuration (use your own local address and port):

```text
VIDEOFETCH_PROXY_URL=http://127.0.0.1:10808
```

Do **not** commit session cookies, passwords, tokens or `.env.local` files. See [backend setup](backend/docs/SETUP.md), [API reference](backend/docs/API.md) and [troubleshooting](backend/docs/TROUBLESHOOTING.md) for more details.

### 2. Run the frontend

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

Open `http://localhost:3000`. The Next.js development configuration proxies `/api/*` to the backend on port 8080. See [frontend documentation](frontend/docs/GETTING_STARTED.md).

### 3. Test an end-to-end conversion

Before testing third-party links, open the **My file** tab, upload a media file you own or have permission to process, choose MP3 or MP4, and download the result after the job completes. This validates the full upload → queue → conversion → file workflow without relying on external extractors.

Postman requests are in [`backend/postman/VideoFetch.postman_collection.json`](backend/postman/VideoFetch.postman_collection.json).

## Repository structure

```text
backend/     Spring Boot API, tests, media tooling setup and Postman collection
frontend/    Next.js application, translations, components and API client
.github/     Continuous-integration workflows for both projects
LICENSE      MIT license
```

## Development and limitations

The application is a **local portfolio project**, not a hardened multi-tenant download service. Before deploying it to the public internet, add authentication, rate limiting, resource quotas, persistent job storage, process isolation, SSRF protection and monitoring.

YouTube, Instagram and TikTok may block or restrict access even when public preview metadata exists; yt-dlp compatibility also changes over time. VideoFetch reports those errors instead of claiming the download succeeded. Use the app only with media you have the right to access and process.

The release has undergone syntax and isolated Java/FFmpeg checks; a full online dependency build and successful live downloads from all supported platforms have **not** been verified. GitHub Actions is configured to test the code on push and pull requests.

## License

Released under the [MIT License](LICENSE).
