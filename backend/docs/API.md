# HTTP API reference

Default base URL: `http://localhost:8080` (Spring Boot). The Next.js client exposes the same `/api/*` routes through its proxy on port 3000.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Liveness |
| GET | `/api/system/tools` | External tool diagnostics |
| POST | `/api/videos/info` | Preview metadata for a validated media URL |
| POST | `/api/downloads` | Queue a permitted remote media job |
| POST | `/api/uploads` | Upload a local media file for conversion (`multipart/form-data`) |
| GET | `/api/downloads/{id}` | Read job status and progress |
| GET | `/api/downloads/{id}/file` | Download the completed output |

### Preview request

```http
POST /api/videos/info
Content-Type: application/json
```

```json
{"url":"https://www.youtube.com/watch?v=VIDEO_ID"}
```

The response may include `title`, `uploader`, `thumbnailUrl`, `durationSeconds`, `platform`, `availableHeights`, `metadataSource`, and `downloadAvailability`. Empty `availableHeights` means **unknown**, not that no format exists. Public metadata never guarantees the media itself can be downloaded.

### Remote processing

```http
POST /api/downloads
Content-Type: application/json
```

```json
{"url":"https://www.youtube.com/watch?v=VIDEO_ID","format":"MP4","maxHeight":720,"rightsConfirmed":true}
```

Submit only sources you are authorized to access and process. A successful job creation is **not** a successful download; poll its status until `COMPLETED` or `FAILED`.

### Local file conversion

```http
POST /api/uploads
Content-Type: multipart/form-data
```

Fields: `file` (a supported local media file), `format` (`MP3` or `MP4`), optional `maxHeight`, and `rightsConfirmed=true`. A sound-only input should be converted to MP3, not MP4.

### Job state

`QUEUED` → `PROCESSING` → `COMPLETED` or `FAILED`. A `FAILED` job may report structured codes such as `AUTH_REQUIRED`, `NETWORK_ERROR`, `FORMAT_UNAVAILABLE`, `TOO_LARGE`, `TOOLS_MISSING` or `EXTRACTION_FAILED`.

Only request `/api/downloads/{id}/file` after `COMPLETED`. Jobs and files are temporary and are cleared according to the configured retention policy. In-memory jobs do not survive application restarts.

Import the [Postman collection](../postman/VideoFetch.postman_collection.json) for examples; the controller implementations are the source of truth if a field changes in later releases.
