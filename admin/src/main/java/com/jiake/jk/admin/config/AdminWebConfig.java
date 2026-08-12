package com.jiake.jk.admin.config;

import com.jiake.jk.admin.interceptor.AdminAuthNInterceptor;
import com.jiake.jk.admin.interceptor.AuditInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    @Autowired
    private AdminAuthNInterceptor adminAuthNInterceptor;
    @Autowired
    private AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthNInterceptor).addPathPatterns("/**").excludePathPatterns("/auth/**");
        registry.addInterceptor(auditInterceptor).addPathPatterns("/**").excludePathPatterns("/auth/**");
    }
}
