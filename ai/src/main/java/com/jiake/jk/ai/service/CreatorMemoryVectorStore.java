package com.jiake.jk.ai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiake.jk.ai.config.CreatorMemoryMilvusConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class CreatorMemoryVectorStore {
    private final MilvusClientV2 client;
    private final CreatorMemoryMilvusConfig config;
    private final EmbeddingModel embeddingModel;

    public CreatorMemoryVectorStore(MilvusClientV2 client, CreatorMemoryMilvusConfig config,
                                    EmbeddingModel embeddingModel) {
        this.client = client;
        this.config = config;
        this.embeddingModel = embeddingModel;
    }

    public void upsert(Long memoryId, Long userId, String content) {
        ensureCollection();
        delete(memoryId);
        float[] vector = embeddingModel.embed(content);
        JsonArray values = new JsonArray();
        for (float value : vector) values.add(value);
        JsonObject row = new JsonObject();
        row.addProperty("id", memoryId);
        row.addProperty("user_id", userId);
        row.addProperty("content", content);
        row.add("vector", values);
        client.insert(InsertReq.builder().collectionName(config.getCollection()).data(List.of(row)).build());
    }

    public List<MemoryHit> search(Long userId, String query, int topK) {
        ensureCollection();
        float[] vector = embeddingModel.embed(query);
        SearchResp response = client.search(SearchReq.builder()
                .collectionName(config.getCollection()).annsField("vector")
                .data(List.of(new FloatVec(vector))).topK(topK)
                .filter("user_id == " + userId)
                .outputFields(List.of("content"))
                .searchParams(Map.of("metric_type", "COSINE", "params", Map.of("nprobe", 10)))
                .build());
        if (response.getSearchResults().isEmpty()) return List.of();
        return response.getSearchResults().getFirst().stream().map(result -> {
            Map<String, Object> entity = result.getEntity();
            return new MemoryHit(Long.valueOf(result.getId().toString()),
                    Objects.toString(entity.get("content"), ""), result.getScore());
        }).toList();
    }

    public void delete(Long memoryId) {
        if (!Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(config.getCollection()).build()))) return;
        client.delete(DeleteReq.builder().collectionName(config.getCollection())
                .ids(List.of(memoryId)).build());
    }

    private synchronized void ensureCollection() {
        if (Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(config.getCollection()).build()))) return;
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(config.getCollection()).description("SW creator preference memory")
                .dimension(config.getDimensions()).primaryFieldName("id")
                .idType(DataType.Int64).vectorFieldName("vector").metricType("COSINE")
                .autoID(false).enableDynamicField(true).build());
    }

    public record MemoryHit(Long id, String content, Float score) { }
}
