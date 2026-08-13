package com.jiake.jk.common.config;

import com.jiake.jk.common.interceptor.UserIdentifierInterceptor;
import com.jiake.jk.common.interceptor.TraceIdInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(1)
public class CommonConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceIdInterceptor()).order(-100);
        registry.addInterceptor(new UserIdentifierInterceptor()).order(-90);
    }
}
