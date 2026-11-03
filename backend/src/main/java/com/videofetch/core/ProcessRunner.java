package com.videofetch.core;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Executes an allowlisted binary with separate argument tokens; no shell interpolation. */
public class ProcessRunner {
    public record Result(int exitCode, boolean timedOut, boolean tooLarge, String output) {
        public boolean success() { return exitCode == 0 && !timedOut && !tooLarge; }
    }
    public Result run(List<String> command, Path workDir, Duration timeout, long maxBytes,
                      Consumer<String> onLine) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    synchronized (output) {
                        output.append(line).append('\n');
                        if (output.length() > 2_000_000) output.delete(0, output.length() - 2_000_000);
                    }
                    onLine.accept(line);
                }
            } catch (IOException ignored) { }
        });
        long end = System.nanoTime() + timeout.toNanos();
        boolean tooLarge = false;
        boolean timedOut = false;
        while (process.isAlive()) {
            if (System.nanoTime() > end) { timedOut = true; break; }
            if (directoryBytes(workDir) > maxBytes) { tooLarge = true; break; }
            process.waitFor(300, TimeUnit.MILLISECONDS);
        }
        if (timedOut || tooLarge) {
            process.destroyForcibly();
        }
        process.waitFor(4, TimeUnit.SECONDS);
        reader.join(Duration.ofSeconds(3));
        int code = process.isAlive() ? -1 : process.exitValue();
        return new Result(code, timedOut, tooLarge, output.toString());
    }
    private long directoryBytes(Path dir) {
        try (var paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0L; }
            }).sum();
        } catch (IOException e) { return 0L; }
    }
}
