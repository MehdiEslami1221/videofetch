# Troubleshooting

## Java compilation error or incorrect SDK

Set **JDK 21** for the project, module and Maven runner in IntelliJ IDEA. Reload the Maven project and run `./mvnw.cmd clean test` from `backend`. Java 8 cannot compile Java 21 source.

## Tool status reports `ready: false`

Open `http://localhost:8080/api/system/tools` and check which tool is unavailable. Run `scripts/setup-tools.ps1` from the `backend` folder. Verify the executables in `tools/bin`, then check the IntelliJ **Working directory** and restart Spring Boot.

## Preview fails but a video plays in a browser

A browser may use a local HTTP proxy while Java uses direct networking. If a local proxy is required, configure `VIDEOFETCH_PROXY_URL=http://127.0.0.1:PORT` for the Spring Boot process. Verify the proxy with `curl.exe -I --proxy http://127.0.0.1:PORT https://www.youtube.com`, substituting your own port. A link preview is a separate operation from accessing a media stream.

## Remote download fails with authorization / bot verification

Third-party services may require login, challenge automated clients, change extractor behavior or restrict individual media. This is not an FFmpeg conversion error. VideoFetch reports the failure rather than claiming a completed job. Do not upload cookies, secrets or tokens to GitHub. Access restrictions and platform terms apply.

## Next.js cannot connect to Spring Boot

Verify `http://localhost:8080/api/health` first and then `http://localhost:3000/api/health`. Check `frontend/.env.local` and ensure `BACKEND_URL=http://127.0.0.1:8080`. Restart `npm run dev` after modifying environment configuration.

## Job not found or file disappeared

Jobs are stored in memory and outputs expire; a restart or TTL expiry removes their availability. Submit a new job. Do not use job storage as permanent file storage.

## Confirm conversion independently of remote platforms

Use the local-upload workflow with a file you own or a synthetic FFmpeg video. This tests the full job, conversion and download response without assuming that external services grant media access.

## Before public deployment

The app is a local development demo. Public hosting requires authentication, request quotas, upload scanning, process sandboxing, stricter SSRF/redirect controls, monitoring, persistent storage and abuse prevention.
