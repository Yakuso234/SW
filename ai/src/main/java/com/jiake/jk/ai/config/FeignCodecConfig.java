package com.jiake.jk.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Supplies the MVC-style converter required by Spring Cloud OpenFeign in this WebFlux service.
 * The converter reuses Boot's ObjectMapper, including Java Time support and shared JSON rules.
 */
@Configuration(proxyBeanMethods = false)
public class FeignCodecConfig {

    @Bean
    @ConditionalOnMissingBean(HttpMessageConverters.class)
    public HttpMessageConverters feignHttpMessageConverters(ObjectMapper objectMapper) {
        return new HttpMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
