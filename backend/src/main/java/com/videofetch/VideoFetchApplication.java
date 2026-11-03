package com.videofetch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VideoFetchApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoFetchApplication.class, args);
    }
}
