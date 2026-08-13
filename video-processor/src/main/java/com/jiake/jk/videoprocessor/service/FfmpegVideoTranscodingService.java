package com.jiake.jk.videoprocessor.service;

import com.jiake.jk.common.utils.AWSUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 使用系统 ffmpeg 转码；容器镜像负责提供二进制，Java 仅负责编排与错误采集。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegVideoTranscodingService implements VideoTranscodingService {

    private final AWSUtils awsUtils;
    private final MeterRegistry meterRegistry;
    private Timer transcodingTimer;
    private Counter transcodingFailureCounter;

    @Value("${sw.video-processing.ffmpeg-command:ffmpeg}")
    private String ffmpegCommand;
    @Value("${sw.video-processing.ffmpeg-timeout-seconds:600}")
    private long timeoutSeconds;

    @PostConstruct
    void registerMetrics() {
        transcodingTimer = Timer.builder("sw.video.transcoding")
                .description("Video transcoding end-to-end duration")
                .register(meterRegistry);
        transcodingFailureCounter = Counter.builder("sw.video.transcoding.failures")
                .description("Video transcoding failures")
                .register(meterRegistry);
    }

    @Override
    public TranscodingResult transcode(Long videoId, String sourceObjectKey) throws Exception {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        Path workDir = Files.createTempDirectory("sw-video-" + videoId + "-");
        try {
            Path source = workDir.resolve("source.mp4");
            Path processed = workDir.resolve("processed.mp4");
            Path cover = workDir.resolve("cover.jpg");
            awsUtils.downloadObject(sourceObjectKey, source);
            runFfmpeg(List.of(ffmpegCommand, "-y", "-i", source.toString(), "-c:v", "libx264", "-preset", "veryfast",
                    "-movflags", "+faststart", "-c:a", "aac", processed.toString()));
            runFfmpeg(List.of(ffmpegCommand, "-y", "-ss", "00:00:01", "-i", processed.toString(), "-frames:v", "1", cover.toString()));
            String suffix = UUID.randomUUID().toString();
            String processedKey = "processed/" + videoId + "/" + suffix + ".mp4";
            String coverKey = "cover/" + videoId + "/" + suffix + ".jpg";
            awsUtils.putObject(processedKey, processed, "video/mp4");
            awsUtils.putObject(coverKey, cover, "image/jpeg");
            return new TranscodingResult(processedKey, coverKey);
        } catch (Exception exception) {
            transcodingFailureCounter.increment();
            throw exception;
        } finally {
            timerSample.stop(transcodingTimer);
            deleteWorkDir(workDir);
        }
    }

    private void runFfmpeg(List<String> command) throws IOException, InterruptedException {
        Path outputFile = Files.createTempFile("sw-ffmpeg-", ".log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            Files.deleteIfExists(outputFile);
            throw new IllegalStateException("FFmpeg 执行超时: " + Duration.ofSeconds(timeoutSeconds));
        }
        if (process.exitValue() != 0) {
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(outputFile);
            throw new IllegalStateException("FFmpeg 执行失败，exitCode=" + process.exitValue() + ", output="
                    + output.substring(0, Math.min(output.length(), 4000)));
        }
        Files.deleteIfExists(outputFile);
    }

    private void deleteWorkDir(Path workDir) {
        if (workDir == null) {
            return;
        }
        try (var files = Files.walk(workDir)) {
            files.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("Failed to remove video work file {}", path, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("Failed to clear video work directory {}", workDir, exception);
        }
    }
}
