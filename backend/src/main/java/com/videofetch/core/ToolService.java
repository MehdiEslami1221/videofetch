package com.videofetch.core;

import java.nio.file.*;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ToolService {
    private final AppSettings settings;
    public ToolService(AppSettings settings) { this.settings = settings; }

    private Path tool(String name) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path local = settings.toolsDir().resolve(name + (windows ? ".exe" : "")).toAbsolutePath().normalize();
        return Files.isRegularFile(local) ? local : null;
    }
    public String executable(String name) {
        Path local = tool(name);
        if (local != null) return local.toString();
        return name; // An executable on PATH can also be used.
    }
    public boolean available(String name) {
        try {
            Process p = new ProcessBuilder(executable(name), name.equals("yt-dlp") ? "--version" : "-version").redirectErrorStream(true).start();
            try (var in = p.getInputStream()) { in.transferTo(java.io.OutputStream.nullOutputStream()); }
            return p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }
    public boolean availableRuntime() {
        String runtime = settings.jsRuntime();
        if (runtime == null || runtime.isBlank()) return false;
        // Runtime name is installation-controlled, never an HTTP request parameter.
        String binary = runtime.split(":", 2)[0];
        if (!binary.matches("(node|deno|bun|quickjs)") ) return false;
        try {
            Process p = new ProcessBuilder(binary, "--version").redirectErrorStream(true).start();
            try (var stream = p.getInputStream()) { stream.transferTo(java.io.OutputStream.nullOutputStream()); }
            return p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }
    public Map<String, Object> state() {
        boolean ytdlp = available("yt-dlp");
        boolean ffmpeg = available("ffmpeg");
        boolean ffprobe = available("ffprobe");
        boolean javascript = availableRuntime();
        return Map.of("ytDlpAvailable", ytdlp, "ffmpegAvailable", ffmpeg,
                "ffprobeAvailable", ffprobe, "ready", ytdlp && ffmpeg && ffprobe,
                "localConversionReady", ffmpeg && ffprobe,
                "proxyConfigured", settings.proxyUrl() != null && !settings.proxyUrl().isBlank(),
                "jsRuntimeAvailable", javascript);
    }
}
