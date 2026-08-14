package com.jiake.jk.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiake.jk.video.pojo.entity.VideoPublishInboxRecovery;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoPublishInboxRecoveryMapper extends BaseMapper<VideoPublishInboxRecovery> {

    @Insert("""
            INSERT IGNORE INTO video_publish_inbox_recovery
            (id, message_digest, video_id, creator_id, trace_id, recovery_attempt, outbox_id, created_at)
            VALUES (#{id}, #{messageDigest}, #{videoId}, #{creatorId}, #{traceId}, #{recoveryAttempt}, #{outboxId}, NOW())
            """)
    int insertIgnore(VideoPublishInboxRecovery recovery);
}
