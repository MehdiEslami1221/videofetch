package com.videofetch.core;

import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableConfigurationProperties(AppSettings.class)
public class AppConfig implements WebMvcConfigurer {
    private final AppSettings settings;
    public AppConfig(AppSettings settings) { this.settings = settings; }

    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor downloadWorkers() {
        return new ThreadPoolExecutor(settings.workers(), settings.workers(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()),
                r -> { Thread t = new Thread(r, "videofetch-job"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public HttpClient metadataClient() {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (settings.proxyUrl() != null && !settings.proxyUrl().isBlank()) {
            URI uri = URI.create(settings.proxyUrl());
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1) {
                throw new IllegalArgumentException("VIDEOFETCH_PROXY_URL requires http://host:port");
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
        }
        return builder.build();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(settings.corsOrigin())
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type");
    }
}
