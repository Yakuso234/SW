package com.jiake.jk.video.pojo.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoProcessingStatusResponseTest {

    @Test
    void shouldDeserializeProjectDateTimeFormatUsedByVideoService() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String body = """
                {"videoId":701,"videoStatus":"PUBLISHED","processingStatus":"SUCCEEDED","retryCount":1,
                "leaseExpireAt":null,"errorMessage":null,"updatedAt":"2026-08-14 08:55:00"}
                """;

        VideoProcessingStatusResponse response = objectMapper.readValue(body, VideoProcessingStatusResponse.class);

        assertEquals(701L, response.videoId());
        assertEquals(Video.VideoStatus.PUBLISHED, response.videoStatus());
        assertEquals(VideoProcessingTask.ProcessingStatus.SUCCEEDED, response.processingStatus());
        assertEquals(LocalDateTime.of(2026, 8, 14, 8, 55), response.updatedAt());
    }
}
