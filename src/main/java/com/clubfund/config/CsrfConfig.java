package com.clubfund.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor;
import org.springframework.web.servlet.support.RequestDataValueProcessor;

/** 统一保护非只读请求，由模板引擎自动为 POST 表单添加令牌。 */
@Configuration
public class CsrfConfig {

    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilter() {
        CsrfFilter filter = new CsrfFilter(new HttpSessionCsrfTokenRepository());
        AccessDeniedHandlerImpl deniedHandler = new AccessDeniedHandlerImpl();
        deniedHandler.setErrorPage("/csrf-error");
        filter.setAccessDeniedHandler(deniedHandler);

        FilterRegistrationBean<CsrfFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public RequestDataValueProcessor requestDataValueProcessor() {
        return new CsrfRequestDataValueProcessor();
    }
}
