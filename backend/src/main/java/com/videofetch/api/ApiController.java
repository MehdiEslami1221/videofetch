package com.videofetch.api;

import com.videofetch.core.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
    public record LinkRequest(@NotBlank @Size(max = 2048) String url) { }
    public record DownloadRequest(@NotBlank @Size(max = 2048) String url, @NotNull JobService.Format format,
                                  @Min(144) @Max(2160) Integer maxHeight, boolean rightsConfirmed) { }
    private final MetadataService previews;
    private final JobService jobs;
    private final ToolService tools;
    public ApiController(MetadataService previews, JobService jobs, ToolService tools) {
        this.previews = previews; this.jobs = jobs; this.tools = tools;
    }
    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "UP", "service", "videofetch"); }
    @GetMapping("/system/tools")
    public Map<String, Object> tools() { return tools.state(); }
    @PostMapping("/videos/info")
    public MetadataService.Preview info(@Valid @RequestBody LinkRequest request) { return previews.fetch(request.url()); }
    @PostMapping("/downloads")
    public ResponseEntity<JobService.JobView> remote(@Valid @RequestBody DownloadRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(jobs.remote(request.url(), request.format(), request.maxHeight(), request.rightsConfirmed()));
    }
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobService.JobView> upload(@RequestParam("file") MultipartFile file,
            @RequestParam("format") JobService.Format format,
            @RequestParam(value = "maxHeight", required = false) Integer maxHeight,
            @RequestParam("rightsConfirmed") boolean rightsConfirmed) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobs.upload(file, format, maxHeight, rightsConfirmed));
    }
    @GetMapping("/downloads/{id}")
    public JobService.JobView status(@PathVariable UUID id) { return jobs.get(id); }
    @GetMapping("/downloads/{id}/file")
    public ResponseEntity<FileSystemResource> file(@PathVariable UUID id) throws java.io.IOException {
        Path file = jobs.file(id);
        String name = file.getFileName().toString().endsWith(".mp3") ? "mp3" : "mp4";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(name.equals("mp3") ? "audio/mpeg" : "video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"videofetch-" + id + "." + name + "\"")
                .cacheControl(CacheControl.noStore()).contentLength(Files.size(file)).body(new FileSystemResource(file));
    }
}
