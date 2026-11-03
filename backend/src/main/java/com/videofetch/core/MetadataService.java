package com.videofetch.core;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MetadataService {
    public record Preview(String platform, String id, String title, String uploader,
                          Long durationSeconds, String thumbnailUrl, String originalUrl,
                          List<Integer> availableHeights, String metadataSource, String downloadAvailability) { }
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ToolService tools;
    private final AppSettings settings;
    private final ProcessRunner runner = new ProcessRunner();
    public MetadataService(HttpClient http, ObjectMapper mapper, ToolService tools, AppSettings settings) {
        this.http = http; this.mapper = mapper; this.tools = tools; this.settings = settings;
    }
    public Preview fetch(String raw) {
        MediaLink link = MediaLink.parse(raw);
        if (!link.platform().equals("INSTAGRAM")) {
            String base = link.platform().equals("YOUTUBE") ? "https://www.youtube.com/oembed?format=json&url=" : "https://www.tiktok.com/oembed?url=";
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + URLEncoder.encode(link.url(), StandardCharsets.UTF_8)))
                        .header("Accept", "application/json")
                        .header("User-Agent", "VideoFetch/1.0")
                        .timeout(Duration.ofSeconds(16)).GET().build();
                HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode data = mapper.readTree(response.body());
                    if (data.path("title").isTextual()) {
                        return new Preview(link.platform(), link.id(), clean(data.path("title").asText()),
                                clean(data.path("author_name").asText("")), null,
                                clean(data.path("thumbnail_url").asText("")), link.url(), List.of(), "OEMBED", "UNKNOWN");
                    }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception ignored) { }
        }
        // The extractor is optional at preview time: failures do not claim the media is downloadable.
        if (tools.available("yt-dlp")) {
            try { return extractor(link); }
            catch (Exception ignored) { }
        }
        return new Preview(link.platform(), link.id(), "", "", null, "", link.url(), List.of(), "LINK_ONLY", "UNKNOWN");
    }
    private Preview extractor(MediaLink link) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("vf-preview-");
        try {
            List<String> args = new ArrayList<>(List.of(tools.executable("yt-dlp"), "--no-config", "--no-plugin-dirs",
                    "--no-playlist", "--skip-download", "--dump-single-json", "--no-warnings", "--socket-timeout", "12"));
            appendNetwork(args);
            args.add("--"); args.add(link.url());
            var r = runner.run(args, dir, Duration.ofSeconds(32), 1_000_000, line -> {});
            if (!r.success()) throw new IOException("Extractor metadata unavailable");
            JsonNode data = mapper.readTree(r.output());
            Set<Integer> heights = new TreeSet<>();
            if (data.path("formats").isArray()) for (JsonNode f : data.path("formats")) {
                int height = f.path("height").asInt(0);
                if (height > 0 && height <= 2160 && !f.path("vcodec").asText("none").equals("none")) heights.add(height);
            }
            return new Preview(link.platform(), link.id(), clean(data.path("title").asText("")),
                    clean(data.path("uploader").asText("")), data.path("duration").isNumber() ? data.path("duration").asLong() : null,
                    clean(data.path("thumbnail").asText("")), link.url(), new ArrayList<>(heights), "EXTRACTOR", "UNKNOWN");
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
            }
        }
    }
    public void appendNetwork(List<String> args) {
        if (settings.proxyUrl() != null && !settings.proxyUrl().isBlank()) {
            args.add("--proxy"); args.add(settings.proxyUrl());
        }
        if (settings.jsRuntime() != null && !settings.jsRuntime().isBlank()) {
            args.add("--js-runtimes"); args.add(settings.jsRuntime());
        }
        // Optional, administrator-provided local session file for content the owner
        // is authorized to access. Never accept a cookie path from an API request.
        if (settings.cookiesFile() != null && !settings.cookiesFile().isBlank()) {
            Path file = Path.of(settings.cookiesFile()).toAbsolutePath().normalize();
            if (Files.isRegularFile(file)) { args.add("--cookies"); args.add(file.toString()); }
        }
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
