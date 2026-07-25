package org.example.common.openai.config;

import lombok.RequiredArgsConstructor;
import org.example.common.openai.interceptor.ApiKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册 API Key 拦截器，仅拦截 /v1/** 路径
 */
@Configuration
@RequiredArgsConstructor
public class OpenAiWebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyInterceptor apiKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns("/v1/**")
                // /v1/models 由 Controller 内部自行决定是否校验，这里先放行
                // 如果要求 models 也校验，去掉下面这行
                .excludePathPatterns("/v1/models");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
