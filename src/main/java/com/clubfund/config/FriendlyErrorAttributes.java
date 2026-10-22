package com.clubfund.config;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/** 统一错误页和 JSON 响应的中文提示，不向客户端输出异常细节。 */
@Component
public class FriendlyErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = (Integer) attributes.get("status");
        // 直接访问 /error 时，框架的属性值为 999，实际响应为 500。
        if (status == 999) {
            status = 500;
            attributes.put("status", status);
        }
        String title;
        String message;
        switch (status) {
            case 400 -> {
                title = "请求信息有误";
                message = "提交的信息不完整或格式有误，请返回检查后重新提交。";
            }
            case 403 -> {
                title = "暂时无法访问";
                message = "当前账号无权访问此内容或执行这项操作，请返回工作台或使用有权限的账号登录。";
            }
            case 404 -> {
                title = "页面或内容不存在";
                message = "页面或内容可能已被删除，也可能是地址有误，请检查地址或返回工作台。";
            }
            case 405 -> {
                title = "请求方式不支持";
                message = "此地址不支持当前操作方式，请返回原页面，使用页面上的按钮操作。";
            }
            default -> {
                title = status >= 500 ? "服务暂时出现异常" : "请求未能完成";
                message = status >= 500
                        ? "服务暂时无法完成请求。请先核对操作结果，避免重复提交，稍后再试。"
                        : "本次请求未能处理，请返回页面检查后重试。";
            }
        }
        attributes.put("error", title);
        attributes.put("message", message);
        return attributes;
    }
}
