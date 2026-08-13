package com.jiake.jk.video.pojo.entity.multi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoWithInteractionStatus {
    private Long id;
    private Long creatorId;
    private String description;
    private String url;
    private Long likes;
    private Long comments;
    private Long favorites;
    private Boolean isLike;
    private Boolean isFavorite;
    private LocalDateTime publishedAt;
    private Long feedId;
}
