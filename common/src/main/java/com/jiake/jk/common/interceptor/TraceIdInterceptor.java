package com.jiake.jk.common.interceptor;

import com.jiake.jk.common.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class TraceIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TraceContext.setTraceId(request.getHeader(TraceContext.TRACE_ID_HEADER));
        response.setHeader(TraceContext.TRACE_ID_HEADER, TraceContext.getOrCreateTraceId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TraceContext.clear();
    }
}
