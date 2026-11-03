package com.videofetch.core;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    public enum Status { QUEUED, PROCESSING, COMPLETED, FAILED }
    public enum Format { MP3, MP4 }
    public record JobView(UUID id, Status status, Format format, int progress, String fileName,
                          Long sizeBytes, String errorCode, String error, Instant createdAt,
                          Instant expiresAt, String fileUrl, String source) { }
    public static final class Job {
        final UUID id = UUID.randomUUID();
        final Format format;
        final Path directory;
        final String source;
        final Instant createdAt = Instant.now();
        volatile Status status = Status.QUEUED;
        volatile int progress;
        volatile String errorCode;
        volatile String error;
        volatile Path output;
        volatile Long sizeBytes;
        volatile Instant expiresAt;
        Job(Format format, Path directory, String source) { this.format = format; this.directory = directory; this.source = source; }
        JobView view() {
            return new JobView(id, status, format, progress, output == null ? null : output.getFileName().toString(),
                    sizeBytes, errorCode, error, createdAt, expiresAt,
                    status == Status.COMPLETED ? "/api/downloads/" + id + "/file" : null, source);
        }
    }
    private final ThreadPoolExecutor workers;
    private final ToolService tools;
    private final AppSettings settings;
    private final MetadataService metadata;
    private final ProcessRunner runner = new ProcessRunner();
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
    private final Path root;
    private static final Pattern PROGRESS = Pattern.compile("VF_PROGRESS:\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern FFMPEG_PROGRESS = Pattern.compile("out_time_(?:us|ms)=(\\d+)");
    public JobService(ThreadPoolExecutor workers, ToolService tools, AppSettings settings, MetadataService metadata) throws IOException {
        this.workers = workers; this.tools = tools; this.settings = settings; this.metadata = metadata;
        root = settings.storageDir().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }
    private Job newJob(Format format, String source) {
        if (workers.getQueue().remainingCapacity() == 0 && workers.getActiveCount() >= settings.workers())
            throw problem(HttpStatus.TOO_MANY_REQUESTS, "QUEUE_FULL", "All processing slots are busy. Try again shortly.");
        Path folder = root.resolve(UUID.randomUUID().toString());
        try { Files.createDirectory(folder); }
        catch (IOException e) { throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not allocate temporary storage."); }
        Job job = new Job(format, folder, source);
        jobs.put(job.id, job);
        return job;
    }
    private void start(Job job, Runnable operation) {
        try { workers.execute(() -> {
            job.status = Status.PROCESSING;
            try { operation.run(); }
            catch (Exception e) { log.warn("Job {} failed: {}", job.id, e.toString()); fail(job, "PROCESS_FAILED", "Processing failed. See backend logs for details."); }
            finally {
                job.expiresAt = Instant.now().plus(Duration.ofMinutes(settings.jobTtlMinutes()));
                if (job.status == Status.FAILED) deleteTree(job.directory);
            }
        }); }
        catch (RejectedExecutionException e) {
            jobs.remove(job.id); deleteTree(job.directory);
            throw problem(HttpStatus.TOO_MANY_REQUESTS, "QUEUE_FULL", "All processing slots are busy. Try again shortly.");
        }
    }
    public JobView remote(String url, Format format, Integer maxHeight, boolean rightsConfirmed) {
        checkRights(rightsConfirmed);
        MediaLink link = MediaLink.parse(url);
        if (!tools.available("yt-dlp") || !tools.available("ffmpeg"))
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "TOOLS_MISSING", "yt-dlp and FFmpeg are required for remote processing.");
        int height = maxHeight == null ? 720 : maxHeight;
        if (height < 144 || height > 2160) throw problem(HttpStatus.BAD_REQUEST, "INVALID_QUALITY", "Select a height between 144p and 2160p.");
        Job job = newJob(format, "REMOTE");
        start(job, () -> processRemote(job, link, height));
        return job.view();
    }
    public JobView upload(MultipartFile file, Format format, Integer maxHeight, boolean rightsConfirmed) {
        checkRights(rightsConfirmed);
        if (!tools.available("ffmpeg") || !tools.available("ffprobe"))
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "TOOLS_MISSING", "FFmpeg and ffprobe are required for local file conversion.");
        if (file.isEmpty() || file.getSize() > settings.maxFileBytes())
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_FILE_SIZE", "Upload a non-empty file smaller than the configured limit.");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!original.matches("(?s).*\\.(mp4|mov|mkv|webm|m4a|mp3|wav)$"))
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "Use MP4, MOV, MKV, WEBM, M4A, MP3, or WAV.");
        if (format == Format.MP4 && original.matches("(?s).*\\.(m4a|mp3|wav)$"))
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_CONVERSION", "Audio-only files can be exported as MP3, not video MP4.");
        int height = maxHeight == null ? 720 : maxHeight;
        if (height < 144 || height > 2160) throw problem(HttpStatus.BAD_REQUEST, "INVALID_QUALITY", "Invalid target quality.");
        Job job = newJob(format, "UPLOAD");
        Path input = job.directory.resolve("source" + original.substring(original.lastIndexOf('.')));
        try { file.transferTo(input); }
        catch (Exception e) { jobs.remove(job.id); deleteTree(job.directory); throw problem(HttpStatus.BAD_REQUEST, "UPLOAD_FAILED", "File could not be saved."); }
        start(job, () -> processLocal(job, input, height));
        return job.view();
    }
    private void processRemote(Job job, MediaLink link, int height) {
        List<String> cmd = new ArrayList<>(List.of(tools.executable("yt-dlp"), "--no-config", "--no-plugin-dirs", "--no-playlist",
                "--no-cache-dir", "--newline", "--progress", "--progress-template", "download:VF_PROGRESS:%(progress._percent_str)s",
                "--socket-timeout", "15", "--retries", "2", "--fragment-retries", "2",
                "--max-filesize", Long.toString(settings.maxFileBytes()),
                "--match-filters", "duration <=? " + settings.maxDurationSeconds(),
                "-o", "media.%(ext)s"));
        Path localFfmpeg = settings.toolsDir().resolve(System.getProperty("os.name").toLowerCase().contains("win") ? "ffmpeg.exe" : "ffmpeg");
        if (Files.isRegularFile(localFfmpeg)) {
            cmd.add("--ffmpeg-location");
            cmd.add(settings.toolsDir().toAbsolutePath().normalize().toString());
        }
        metadata.appendNetwork(cmd);
        if (job.format == Format.MP3) cmd.addAll(List.of("-x", "--audio-format", "mp3", "--audio-quality", "4"));
        else cmd.addAll(List.of("-f", "bv*[height<=" + height + "]+ba/b[height<=" + height + "]/bestvideo+bestaudio/best",
                "--merge-output-format", "mp4", "--recode-video", "mp4"));
        cmd.add("--"); cmd.add(link.url());
        runJob(job, cmd, 0);
    }
    private void processLocal(Job job, Path input, int height) {
        double seconds = probeDuration(input, job.directory);
        List<String> cmd = new ArrayList<>(List.of(tools.executable("ffmpeg"), "-hide_banner", "-nostdin", "-y", "-progress", "pipe:1", "-nostats", "-i", input.toAbsolutePath().toString()));
        if (job.format == Format.MP3) {
            cmd.addAll(List.of("-vn", "-c:a", "libmp3lame", "-q:a", "4", "media.mp3"));
        } else {
            cmd.addAll(List.of("-vf", "scale='min(iw," + height * 4 + ")':'min(ih," + height + ")':force_original_aspect_ratio=decrease:force_divisible_by=2",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", "-c:a", "aac", "-movflags", "+faststart", "media.mp4"));
        }
        runJob(job, cmd, seconds);
    }
    private double probeDuration(Path input, Path work) {
        try {
            var r = runner.run(List.of(tools.executable("ffprobe"), "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", input.toAbsolutePath().toString()),
                    work, Duration.ofSeconds(12), settings.maxFileBytes() * 2, line -> {});
            return r.success() ? Math.max(0, Double.parseDouble(r.output().strip())) : 0;
        } catch (Exception e) { return 0; }
    }
    private void runJob(Job job, List<String> command, double durationSeconds) {
        try {
            ProcessRunner.Result result = runner.run(command, job.directory,
                    Duration.ofSeconds(settings.processTimeoutSeconds()), settings.maxFileBytes() * 2,
                    line -> { Matcher m = PROGRESS.matcher(line);
                        if (m.find()) try { job.progress = Math.max(job.progress, Math.min(95, (int) Double.parseDouble(m.group(1)))); }
                        catch (NumberFormatException ignored) { }
                        if (durationSeconds > 0) {
                            Matcher time = FFMPEG_PROGRESS.matcher(line);
                            if (time.find()) try {
                                double elapsed = Double.parseDouble(time.group(1)) / 1_000_000.0;
                                job.progress = Math.max(job.progress, Math.min(95, (int) (elapsed * 95 / durationSeconds)));
                            } catch (NumberFormatException ignored) { }
                        }
                    });
            if (!result.success()) {
                String output = result.output().toLowerCase(Locale.ROOT);
                if (result.timedOut()) { fail(job, "TIMEOUT", "Processing timed out."); return; }
                if (result.tooLarge()) { fail(job, "TOO_LARGE", "Media exceeded the size limit."); return; }
                log.warn("Media extraction job {} failed (exit {}, last output: {})", job.id, result.exitCode(), briefError(result.output()));
                if (output.contains("sign in to confirm") || output.contains("login required")
                    || output.contains("cookies are no longer valid") || output.contains("authentication required")
                    || output.contains("login to view") || output.contains("private account"))
                    fail(job, "AUTH_REQUIRED", "The platform requires valid authorization. Media is not accessible to this server.");
                else if (output.contains("http error 429") || output.contains("too many requests") || output.contains("rate limit"))
                    fail(job, "RATE_LIMITED", "The source is rate limiting requests. Try again later.");
                else if (output.contains("http error 403") || output.contains("forbidden") || output.contains("po token"))
                    fail(job, "ACCESS_DENIED", "The media source rejected this request. Its access restrictions cannot be overridden by this application.");
                else if (output.contains("requested format is not available") || output.contains("no video formats found"))
                    fail(job, "FORMAT_UNAVAILABLE", "The requested format is not available from the source.");
                else if (output.contains("timed out") || output.contains("proxyerror") || output.contains("unable to download"))
                    fail(job, "NETWORK_ERROR", "The source could not be reached. Check proxy and network settings.");
                else fail(job, "EXTRACTION_FAILED", "The source did not provide an accessible media file, or conversion failed.");
                return;
            }
            Path expected = job.directory.resolve("media." + job.format.name().toLowerCase(Locale.ROOT));
            if (!Files.isRegularFile(expected) || Files.size(expected) < 1)
                { fail(job, "OUTPUT_MISSING", "No output file was produced."); return; }
            if (Files.size(expected) > settings.maxFileBytes())
                { fail(job, "TOO_LARGE", "Output exceeded the size limit."); return; }
            job.output = expected;
            job.sizeBytes = Files.size(expected);
            job.progress = 100;
            job.status = Status.COMPLETED;
        } catch (Exception e) {
            fail(job, "PROCESS_FAILED", "Processing failed. Check the backend terminal.");
        }
    }
    private static String briefError(String raw) {
        // Avoid printing user-provided URLs, full command arguments, or cookies in logs.
        String last = "";
        for (String line : raw.split("\\R")) if (line.startsWith("ERROR:") || line.startsWith("WARNING:")) last = line;
        String safe = last.replaceAll("https?://\\S+", "[URL]");
        return safe.substring(0, Math.min(300, safe.length()));
    }
    private void fail(Job job, String code, String message) { job.errorCode = code; job.error = message; job.status = Status.FAILED; }
    private void checkRights(boolean confirmed) {
        if (!confirmed) throw problem(HttpStatus.BAD_REQUEST, "PERMISSION_REQUIRED", "Confirm you own the media or have permission to process it.");
    }
    private ApiProblem problem(HttpStatus status, String code, String message) { return new ApiProblem(status, code, message); }
    public JobView get(UUID id) { return lookup(id).view(); }
    public Path file(UUID id) {
        Job job = lookup(id);
        if (job.status != Status.COMPLETED || job.output == null) throw problem(HttpStatus.CONFLICT, "NOT_READY", "Output is not ready yet.");
        if (!job.output.normalize().startsWith(job.directory) || !Files.isRegularFile(job.output))
            throw problem(HttpStatus.GONE, "FILE_EXPIRED", "The output has expired.");
        return job.output;
    }
    private Job lookup(UUID id) {
        Job job = jobs.get(id);
        if (job == null) throw problem(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found or expired.");
        return job;
    }
    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        Instant now = Instant.now();
        jobs.forEach((id, job) -> {
            if (job.expiresAt != null && now.isAfter(job.expiresAt) && jobs.remove(id, job)) deleteTree(job.directory);
        });
    }
    private void deleteTree(Path folder) {
        if (!folder.toAbsolutePath().normalize().startsWith(root)) return;
        try (var paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }
}
