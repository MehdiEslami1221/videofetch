package com.videofetch.core;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Narrow allowlist: only explicit media URLs from supported services are accepted. */
public record MediaLink(String url, String platform, String id) {
    private static final Set<String> YOUTUBE = Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be");
    private static final Set<String> INSTAGRAM = Set.of("instagram.com", "www.instagram.com");
    private static final Set<String> TIKTOK = Set.of("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com");
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public static MediaLink parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 2048) throw invalid();
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getFragment() != null || uri.getHost() == null) throw invalid();
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String platform;
            String id;
            if (YOUTUBE.contains(host)) {
                platform = "YOUTUBE";
                if (host.equals("youtu.be")) {
                    id = path.replaceAll("^/|/$", "");
                } else if (path.equals("/watch") || path.equals("/watch/")) {
                    id = "";
                    String query = uri.getRawQuery();
                    if (query != null) for (String part : query.split("&")) {
                        if (part.startsWith("v=")) id = part.substring(2);
                    }
                } else if (path.matches("/(shorts|live)/[A-Za-z0-9_-]{11}/?")) {
                    String[] chunks = path.split("/");
                    id = chunks[2];
                } else throw invalid();
                if (!YOUTUBE_ID.matcher(id).matches()) throw invalid();
            } else if (INSTAGRAM.contains(host)) {
                platform = "INSTAGRAM";
                // Posts, Reels, IGTV, individual public story URLs, and highlight URLs.
                // Availability is decided by Instagram, not by this validation.
                if (path.matches("/(p|reel|tv)/[A-Za-z0-9_-]+/?")) {
                    String[] chunks = path.split("/"); id = chunks[2];
                } else if (path.matches("/stories/[A-Za-z0-9._]+/[0-9]+/?")) {
                    String[] chunks = path.split("/"); id = chunks[3];
                } else if (path.matches("/stories/highlights/[0-9]+/?")) {
                    String[] chunks = path.split("/"); id = chunks[3];
                } else throw invalid();
            } else if (TIKTOK.contains(host)) {
                platform = "TIKTOK";
                if (host.equals("vm.tiktok.com") || host.equals("vt.tiktok.com")) {
                    if (!path.matches("/[A-Za-z0-9]+/?")) throw invalid();
                    id = path.replace("/", "");
                } else if (path.matches("/@[A-Za-z0-9._]+/video/[0-9]+/?")) {
                    String[] chunks = path.split("/"); id = chunks[3];
                } else throw invalid();
            } else throw invalid();
            return new MediaLink(uri.toASCIIString(), platform, id);
        } catch (IllegalArgumentException ex) { throw invalid(); }
    }
    private static ApiProblem invalid() {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "UNSUPPORTED_URL",
            "Enter a supported YouTube video, Instagram post/reel/story, or TikTok video URL.");
    }
}
