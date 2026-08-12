package com.jiake.jk.video.mapstruct;

import com.jiake.jk.video.pojo.entity.VideoUserComment;
import com.jiake.jk.video.pojo.entity.multi.CommentWithReceiver;
import com.jiake.jk.video.pojo.request.PostCommentRequest;
import com.jiake.jk.video.pojo.response.GetDirectCommentResponse;
import com.jiake.jk.video.pojo.response.GetReplyCommentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface InteractionMapStruct {

    InteractionMapStruct INSTANCE = Mappers.getMapper(InteractionMapStruct.class);

    VideoUserComment toVideoUserComment(PostCommentRequest postCommentRequest);

    GetDirectCommentResponse toGetDirectCommentResponse(VideoUserComment videoUserComment);

    GetReplyCommentResponse toGetReplyCommentResponse(CommentWithReceiver commentWithReceiver);
}
