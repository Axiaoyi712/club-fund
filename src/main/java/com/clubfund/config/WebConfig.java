package com.clubfund.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册登录拦截器，并指定哪些路径不用拦截。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")                          // 拦截所有路径
                .excludePathPatterns("/login", "/register",      // 登录注册页不拦截
                        "/css/**", "/js/**", "/error", "/csrf-error");          // 静态资源和错误页不拦截
    }
}
