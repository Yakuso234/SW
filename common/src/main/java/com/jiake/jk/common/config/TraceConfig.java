package com.jiake.jk.common.config;

import com.jiake.jk.common.trace.TraceContext;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TraceConfig {

    @Bean
    public RequestInterceptor traceIdFeignRequestInterceptor() {
        return template -> template.header(TraceContext.TRACE_ID_HEADER, TraceContext.getOrCreateTraceId());
    }
}
