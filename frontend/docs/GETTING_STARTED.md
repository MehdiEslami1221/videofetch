# Web client setup

The Next.js frontend requires **Node.js 22 or later** and a running VideoFetch Spring Boot API.

## Start the API

Follow [backend setup](../../backend/docs/SETUP.md). The backend should respond to `http://localhost:8080/api/health` before you start the browser client.

## Start Next.js

Open a terminal in the `frontend` folder:

```powershell
node -v
npm -v
Copy-Item .env.example .env.local
npm install
npm run dev
```

Open `http://localhost:3000` and verify the API proxy at `http://localhost:3000/api/health`. `BACKEND_URL` in `.env.local` defaults to `http://127.0.0.1:8080` and is used by the server-side Next.js rewrite. Restart Next.js after changing it.

## Using the app

1. Choose **English** or **Persian** and optionally switch the theme.
2. To inspect a link, paste a supported YouTube, Instagram or TikTok URL. The page displays the preview information the source exposes, which may be incomplete.
3. Select MP4 or MP3 and a target height when relevant. A target height is a preference unless the extractor has confirmed available streams.
4. Confirm you have permission to process the media, then submit the job.
5. Track progress. Download the output only when the status is `COMPLETED`. When a platform blocks access, read the error details; the UI does not claim success.
6. For a reliable test, use **My file** to upload media you own, choose MP3/MP4 and export the result without requiring an external platform.

## Quality checks

```powershell
npm run check
npm run typecheck
npm run build
```

The repository ignores `.env.local`, `.next/` and `node_modules/`. Commit a generated `package-lock.json` after installing dependencies locally; this provides reproducible installs in future updates.

## Notes

The application does not provide Instagram username-based Story discovery. Public thumbnail access is not proof of download availability. Jobs and outputs are temporary and stored by the backend, not by the Next.js process. For HTTP errors, consult [API reference](../../backend/docs/API.md) and [troubleshooting](../../backend/docs/TROUBLESHOOTING.md).
