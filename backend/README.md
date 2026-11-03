# VideoFetch API

Spring Boot backend for media preview, permission-based remote media processing, and local MP3/MP4 conversion. Designed as a **local portfolio project**, with a bilingual Next.js client in the companion archive.

## Stack

- Java 21 / Spring Boot 4.1.2 / Maven
- `yt-dlp` and `FFmpeg` (`ffprobe` included)
- Java HTTP client with explicit proxy configuration, async bounded thread pool, in-memory jobs and TTL cleanup
- No Docker and no database

## Quick start — Windows + IntelliJ IDEA

1. Open **this backend folder** in IntelliJ, set **Project SDK = JDK 21**, import Maven.
2. In **Run → Edit Configurations**, set **Working directory** to this folder (the one containing `pom.xml`).
3. In IntelliJ Terminal, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-tools.ps1
.\mvnw.cmd clean test
```

The setup downloads Windows x64 tools from the original publishers and validates checksums. Maven dependencies are handled by Maven, while the external executables live in `tools/bin` (ignored by Git). Install Node.js >=22 for the Next.js frontend; `yt-dlp` uses `--js-runtimes node`. Deno is another supported runtime but requires updating `VIDEOFETCH_JS_RUNTIME`.

4. In IntelliJ, **Run** `com.videofetch.VideoFetchApplication`. Alternatively:

```powershell
.\mvnw.cmd spring-boot:run
```

5. Check:

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/api/system/tools`

If `ready` is false, check files in `tools/bin` and **Working directory**. If only `localConversionReady` is true, local file conversion can still work.

## Proxy / restricted networks

Your environment may require a local HTTP proxy. Configure **environment variables** for the IntelliJ run configuration, not just Java VM properties:

```text
VIDEOFETCH_PROXY_URL=http://127.0.0.1:10808
```

Both Java's oEmbed HTTP client and subprocess `yt-dlp` use this value. `-Dhttps.proxyHost=...` by itself does **not** configure external `yt-dlp.exe`. You can leave this variable empty on networks without a proxy. Proxy credentials should not be committed.

For a direct command-line diagnostic (no downloading):

```powershell
.\tools\bin\yt-dlp.exe --proxy "http://127.0.0.1:10808" --js-runtimes node --skip-download --print title "https://www.youtube.com/watch?v=VIDEO_ID"
```

An oEmbed preview can succeed while the media extractor fails due to site authorization, anti-bot checks, geographic rules or format availability. This app never promises the stream is obtainable based on the thumbnail alone. YouTube may require EJS with its latest official executable / JS runtime and may still deny access. Do not commit cookies, credentials, account tokens, or use this demo to evade platform restrictions.

## HTTP API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/health` | Liveness |
| GET | `/api/system/tools` | CLI tool diagnostics |
| POST | `/api/videos/info` | Public preview (`{ "url": "https://..." }`) |
| POST | `/api/downloads` | Queue authorized remote processing |
| POST | `/api/uploads` | Upload owned media for conversion (multipart) |
| GET | `/api/downloads/{id}` | Poll job status |
| GET | `/api/downloads/{id}/file` | Fetch file after completion |

Remote request:

```json
{
  "url": "https://www.youtube.com/watch?v=VIDEO_ID",
  "format": "MP4",
  "maxHeight": 720,
  "rightsConfirmed": true
}
```

Upload multipart fields: `file` (binary, one file), `format` (`MP3` or `MP4`), `maxHeight` (e.g. `720`), `rightsConfirmed` (`true`). For videos without public metadata, `/api/videos/info` returns `metadataSource: LINK_ONLY` instead of claiming downloadable formats. `availableHeights: []` means unknown, not zero support.

Job status: `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`. Errors include `AUTH_REQUIRED`, `NETWORK_ERROR`, `FORMAT_UNAVAILABLE`, `TOO_LARGE`, `TOOLS_MISSING`, and `EXTRACTION_FAILED`. A failure is a real failure, **not** 100% completion.

## Validate local conversion end-to-end

Generate a short synthetic video with FFmpeg (no copyrighted media required):

```powershell
.\tools\bin\ffmpeg.exe -f lavfi -i "testsrc2=size=640x360:rate=24:duration=5" -f lavfi -i "sine=frequency=440:duration=5" -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -y sample.mp4
```

Then open Next.js, choose **My file**, select `sample.mp4`, select MP3, confirm rights, run and download the file. This checks upload → queue → FFmpeg → file response. It does **not** prove platforms will allow downloading external videos.

## Source layout

```text
src/main/java/com/videofetch/
  api/            REST controllers and error mapping
  core/           URL policy, preview, workers, processes and configuration
src/test/         URL validation and subprocess tests
scripts/          Windows tool setup and verified Maven bootstrap
postman/          Collection with API examples
data/jobs/        Temporary outputs (gitignored)
```

## Limits and deployment security

Default local limits: 120 MiB input, 120 MiB output, 60 min source duration for remote requests, 2 concurrent worker threads, queue of 6, 60-minute job retention. Jobs are kept **in memory** and disappear on restart; temporary directories from an abrupt shutdown may require manual cleanup.

**Not production-ready for untrusted public traffic.** Before internet exposure implement authenticated users, per-user quotas, rate limits, request size/CPU/network budgets, malware/content scanning for uploads, isolation/sandboxing, an object store, stronger SSRF/redirect enforcement, security logging, and persistent job storage. The allowlist only restricts the top-level URL; extractors can follow media URLs. Keep this service local or trusted until hardened.

Third-party terms and media rights apply. The preview APIs do not grant redistribution or download rights.

## Troubleshooting

- IntelliJ Java 8/21 error: set Project SDK and Module SDK to JDK 21, Maven runner JDK 21, reload Maven.
- `OFFLINE`: backend not running on 8080, or Next.js rewrite points elsewhere.
- `TOOLS_MISSING`: wrong Working directory, missing `yt-dlp.exe` / `ffmpeg.exe`, or CLI check failed.
- `AUTH_REQUIRED`: remote site disallowed access; do not call it a conversion problem.
- `NETWORK_ERROR`: check `VIDEOFETCH_PROXY_URL` and outgoing network.
- `LINK_ONLY`: public preview was inaccessible; a successful remote download is not implied.

See `docs/SETUP.md` for the extended setup guide. No media assets or account data are tracked in Git.

## Changes in this reviewed release

- Instagram direct Story and Highlight link validation; account-name-only discovery is intentionally not advertised.
- End-to-end rights confirmation is passed from the UI into the backend, rather than hard-coded by API helpers.
- Java and yt-dlp share the optional `VIDEOFETCH_PROXY_URL`; Node runtime status is included in `/api/system/tools`.
- Optional `VIDEOFETCH_COOKIES_FILE` for an operator-provided, *authorized* local session file. Never commit it or expose it to public users.
- Structured error codes distinguish authorization, rate limiting, access denied, format unavailability and network errors.
- MP3/MP4 local conversion tests are possible independently from remote platform availability.

For a full setup guide, see `docs/TROUBLESHOOTING.md`. Complete, zero-restriction downloading of every platform and account is **not achievable** with these tools; access controls must be respected.
