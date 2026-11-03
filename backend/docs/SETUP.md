# Backend setup (Windows)

This guide assumes the repository has been cloned and its `backend` and `frontend` folders are available. Use **JDK 21**, **Node.js 22+**, Windows PowerShell, and IntelliJ IDEA or another Java IDE. The repository contains no Docker configuration or database requirement.

## 1. Open and configure the Java project

1. Open the **`backend`** folder (not the repository root) as a Maven project in IntelliJ IDEA.
2. In **File → Project Structure**, select **JDK 21** as both the project and module SDK. Set language level to 21.
3. In **Settings → Build Tools → Maven**, ensure the Maven runner uses JDK 21.
4. In **Run → Edit Configurations**, set the working directory to the absolute path of `backend` (the folder containing `pom.xml`). This matters because the default tool and data paths are relative to the working directory.

## 2. Install native media tools locally

Open a PowerShell terminal **inside `backend`** and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-tools.ps1
```

The installer downloads SHA-256-verified Windows x64 builds of `yt-dlp` and FFmpeg/ffprobe to the ignored `backend/tools/bin` directory. It requires internet access to the original publishers. No administrative installation or global PATH changes are required.

Verify the binaries:

```powershell
.\tools\bin\yt-dlp.exe --version
.\tools\bin\ffmpeg.exe -version
.\tools\bin\ffprobe.exe -version
node -v
```

The bundled `mvnw.cmd` is a Windows bootstrap that retrieves a local Maven distribution as needed. Alternatively, use an installed Maven (`mvn`).

## 3. Build and run Spring Boot

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

The API is served at **http://localhost:8080**. Open these endpoints to confirm the application and its tools:

- `http://localhost:8080/api/health`
- `http://localhost:8080/api/system/tools`

Do not continue to remote download testing until the tool status is understood. Local upload/conversion can work even when a remote extractor is restricted.

## 4. Optional HTTP proxy

If outgoing traffic requires a local HTTP proxy, set the following **environment variable** on the IntelliJ run configuration or PowerShell process:

```powershell
$env:VIDEOFETCH_PROXY_URL = 'http://127.0.0.1:10808'
```

Replace the example port with the actual local HTTP proxy port. The application passes this value to both the Java HTTP client and the external `yt-dlp` process. JVM `-Dhttps.proxyHost` flags alone do not affect external executables.

Session/cookie paths (if needed for access you are authorized to use) should be configured outside the repository and never committed. Avoid storing credentials in shell history or issue comments.

## 5. Test a complete conversion

Generate a synthetic video using the installed FFmpeg:

```powershell
.\tools\bin\ffmpeg.exe -f lavfi -i "testsrc2=size=640x360:rate=24:duration=5" -f lavfi -i "sine=frequency=440:duration=5" -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -y sample.mp4
```

Launch the frontend as described in [the frontend README](../../frontend/README.md), then use the **My file** tab to upload `sample.mp4`, select MP3, confirm processing rights, and download the output when the job reports `COMPLETED`.

For API testing, import [`postman/VideoFetch.postman_collection.json`](../postman/VideoFetch.postman_collection.json) into Postman and use `http://localhost:8080` as the base URL.
