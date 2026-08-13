package com.jiake.jk.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

/** HTTP、Feign 与异步消息共享的轻量链路标识上下文。 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_KEY = "traceId";
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String getOrCreateTraceId() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            setTraceId(traceId);
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        TRACE_ID_HOLDER.set(traceId);
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
        MDC.remove(TRACE_ID_KEY);
    }
}
