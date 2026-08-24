package com.jiake.jk.ai.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class CreatorMemoryMilvusConfig {
    private String uri = "http://localhost:19530";
    private String collection = "creator_memory";
    private int dimensions = 768;

    @Bean(destroyMethod = "close")
    public MilvusClientV2 creatorMemoryMilvusClient() {
        return new MilvusClientV2(ConnectConfig.builder().uri(uri).build());
    }
}

