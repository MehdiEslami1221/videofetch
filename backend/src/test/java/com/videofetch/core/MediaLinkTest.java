package com.videofetch.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaLinkTest {
    @Test void acceptsYouTubeWatchAndShorts() {
        assertEquals("YOUTUBE", MediaLink.parse("https://www.youtube.com/watch?v=L1x3MRcgHw0").platform());
        assertEquals("L1x3MRcgHw0", MediaLink.parse("https://youtu.be/L1x3MRcgHw0").id());
        assertEquals("YOUTUBE", MediaLink.parse("https://youtube.com/shorts/L1x3MRcgHw0").platform());
    }
    @Test void acceptsReelsAndTikTok() {
        assertEquals("INSTAGRAM", MediaLink.parse("https://www.instagram.com/reel/AbCd1234/").platform());
        assertEquals("TIKTOK", MediaLink.parse("https://www.tiktok.com/@example/video/7412345678901234567").platform());
    }
    @Test void acceptsInstagramStoriesAndHighlights() {
        assertEquals("INSTAGRAM", MediaLink.parse("https://www.instagram.com/stories/example.user/12345678901234567/").platform());
        assertEquals("12345678901234567", MediaLink.parse("https://www.instagram.com/stories/example.user/12345678901234567/").id());
        assertEquals("INSTAGRAM", MediaLink.parse("https://www.instagram.com/stories/highlights/18123456789012345/").platform());
        assertThrows(ApiProblem.class, () -> MediaLink.parse("https://www.instagram.com/stories/example.user/"));
    }
    @Test void rejectsNonHttpsAndUnrecognizedHosts() {
        assertThrows(ApiProblem.class, () -> MediaLink.parse("http://youtube.com/watch?v=L1x3MRcgHw0"));
        assertThrows(ApiProblem.class, () -> MediaLink.parse("https://youtube.com.evil.test/watch?v=L1x3MRcgHw0"));
        assertThrows(ApiProblem.class, () -> MediaLink.parse("https://localhost/admin"));
        assertThrows(ApiProblem.class, () -> MediaLink.parse("https://user:password@youtube.com/watch?v=L1x3MRcgHw0"));
    }
}
