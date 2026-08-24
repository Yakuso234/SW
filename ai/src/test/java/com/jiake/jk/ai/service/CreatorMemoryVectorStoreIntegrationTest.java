package com.jiake.jk.ai.service;

import com.jiake.jk.ai.config.CreatorMemoryMilvusConfig;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "SW_RUN_MILVUS_IT", matches = "true")
class CreatorMemoryVectorStoreIntegrationTest {

    @Test
    void shouldIndexSearchIsolateAndDeleteMemoryAgainstRealMilvus() throws Exception {
        String collection = "creator_memory_it_" + System.currentTimeMillis();
        CreatorMemoryMilvusConfig config = new CreatorMemoryMilvusConfig();
        config.setUri("http://127.0.0.1:19530");
        config.setCollection(collection);
        config.setDimensions(768);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(vector());

        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder().uri(config.getUri()).build());
        try {
            CreatorMemoryVectorStore store = new CreatorMemoryVectorStore(client, config, embeddingModel);
            store.upsert(90001L, 70001L, "偏好简短标题");

            List<CreatorMemoryVectorStore.MemoryHit> ownerHits = awaitHits(store, 70001L);
            assertEquals(90001L, ownerHits.getFirst().id());
            assertTrue(store.search(70002L, "简短标题", 3).isEmpty(), "其他用户不能召回该记忆");

            store.delete(90001L);
            assertTrue(awaitDeleted(store, 70001L), "删除后不应继续召回记忆");
        } finally {
            try {
                client.dropCollection(DropCollectionReq.builder().collectionName(collection).build());
            } finally {
                client.close();
            }
        }
    }

    private List<CreatorMemoryVectorStore.MemoryHit> awaitHits(CreatorMemoryVectorStore store, Long userId)
            throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            List<CreatorMemoryVectorStore.MemoryHit> hits = store.search(userId, "简短标题", 3);
            if (!hits.isEmpty()) return hits;
            Thread.sleep(200);
        }
        throw new AssertionError("Milvus insert was not visible in time");
    }

    private boolean awaitDeleted(CreatorMemoryVectorStore store, Long userId) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (store.search(userId, "简短标题", 3).isEmpty()) return true;
            Thread.sleep(200);
        }
        return false;
    }

    private float[] vector() {
        float[] vector = new float[768];
        vector[0] = 1.0F;
        return vector;
    }
}
