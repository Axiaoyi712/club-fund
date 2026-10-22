package com.clubfund.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：负责"没登录就不让进"。
 * 每个请求进来都会先经过 preHandle：
 * - 如果 session 里有登录用户 → 放行（返回 true）
 * - 如果没有 → 跳转到登录页（返回 false）
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object loginUser = request.getSession().getAttribute("loginUser");
        if (loginUser == null) {
            response.sendRedirect("/login");
            return false;
        }
        return true;
    }
}
