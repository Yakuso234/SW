package com.jiake.jk.live.mapstruct;

import com.jiake.jk.live.document.LiveDocument;
import com.jiake.jk.live.response.GetLiveResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface LiveMapstruct {
    LiveMapstruct INSTANCE = Mappers.getMapper(LiveMapstruct.class);

    GetLiveResponse liveDocumentToGetLiveResponse(LiveDocument liveDocument);
}
