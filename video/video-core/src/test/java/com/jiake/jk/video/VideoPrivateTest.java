package com.jiake.jk.video;

import com.jiake.jk.video.controller._private.VideoPrivateController;
import com.jiake.jk.video.service.VideoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VideoPrivateTest {

    @InjectMocks
    private VideoPrivateController videoPrivateController;
    @Mock
    private VideoService videoService;

    @Test
    void putVideoStatusToPublished_shouldDelegateToService() {
        Long videoId = 1001L;

        videoPrivateController.putVideoStatusToPublished(videoId);

        verify(videoService).putVideoStatusToPublished(videoId);
    }
}
