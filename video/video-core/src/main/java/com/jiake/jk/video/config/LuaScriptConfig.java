package com.jiake.jk.video.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class LuaScriptConfig {

    @Bean
    public RedisScript<Long> interactionScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/Interaction.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> flashSaleReserveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/FlashSaleReserve.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> flashSaleReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/FlashSaleRelease.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> flashSaleRestockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/FlashSaleRestock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
