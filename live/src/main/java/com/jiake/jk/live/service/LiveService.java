package com.jiake.jk.live.service;

import com.jiake.jk.live.request.StartLiveRequest;
import com.jiake.jk.live.response.GetLiveResponse;
import org.apache.coyote.BadRequestException;

import java.io.IOException;
import java.util.List;

public interface LiveService {
    String postStartLive(Long userId, StartLiveRequest startLiveRequest) throws IOException;

    void publishCallback(Long roomId, String clientId, String token) throws BadRequestException;

    void unpublishCallback(Long roomId, String clientId) throws BadRequestException;

    void play(Long roomId, String clientId);

    void stop(Long roomId, String clientId);

    Integer getOnline(Long roomId);

    List<GetLiveResponse> getLivesByQuery(String query);
}
