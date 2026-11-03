package com.videofetch.core;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "videofetch")
public record AppSettings(
        Path toolsDir,
        Path storageDir,
        String proxyUrl,
        String jsRuntime,
        String cookiesFile,
        long maxFileBytes,
        long maxDurationSeconds,
        int workers,
        int queueCapacity,
        int jobTtlMinutes,
        int processTimeoutSeconds,
        String corsOrigin) {
}
