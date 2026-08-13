package com.jiake.jk.common.interceptor;

import com.jiake.jk.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdInterceptorTest {

    private final TraceIdInterceptor interceptor = new TraceIdInterceptor();

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    @Test
    void shouldReuseIncomingTraceIdAndReturnItInResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.TRACE_ID_HEADER, "upload-trace-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertEquals("upload-trace-001", TraceContext.getOrCreateTraceId());
        assertEquals("upload-trace-001", response.getHeader(TraceContext.TRACE_ID_HEADER));
    }

    @Test
    void shouldGenerateAndClearTraceIdWhenRequestDoesNotProvideOne() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(new MockHttpServletRequest(), response, new Object());
        String traceId = response.getHeader(TraceContext.TRACE_ID_HEADER);

        assertNotNull(traceId);
        interceptor.afterCompletion(new MockHttpServletRequest(), response, new Object(), null);
        TraceContext.clear();
        assertNull(org.slf4j.MDC.get(TraceContext.TRACE_ID_KEY));
    }
}
