package com.jiake.jk.live.repository;

import com.jiake.jk.live.document.LiveDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface LiveRepository extends ElasticsearchRepository<LiveDocument, Long> {
}
