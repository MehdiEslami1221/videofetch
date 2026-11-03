package com.videofetch.core;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {
    @Test void capturesExitCodeAndOutput() throws Exception {
        var directory = Files.createTempDirectory("vf-process-test-");
        try {
            var result = new ProcessRunner().run(List.of(java.nio.file.Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString(), "-version"),
                    directory, Duration.ofSeconds(10), 1_000_000, line -> {});
            assertTrue(result.success());
            assertTrue(result.output().contains("version"));
        } finally { Files.deleteIfExists(directory); }
    }
}
