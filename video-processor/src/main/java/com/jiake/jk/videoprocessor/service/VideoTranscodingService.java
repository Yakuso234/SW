package com.jiake.jk.videoprocessor.service;

/** 处理一条视频并返回已上传到对象存储的结果 key。 */
public interface VideoTranscodingService {

    TranscodingResult transcode(Long videoId, String sourceObjectKey) throws Exception;

    record TranscodingResult(String processedVideoKey, String coverKey) { }
}
