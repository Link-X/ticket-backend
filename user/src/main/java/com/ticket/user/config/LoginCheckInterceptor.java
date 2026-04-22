package com.ticket.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.result.Result;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 登录检查拦截器：有 @NoLogin 放行，否则要求 SecurityContext 中存在有效认证
 */
@Component
public class LoginCheckInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public LoginCheckInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        // 方法或类上有 @NoLogin 则直接放行
        if (method.hasMethodAnnotation(NoLogin.class)
                || method.getBeanType().isAnnotationPresent(NoLogin.class)) {
            return true;
        }
        // 检查是否已通过 JWT 过滤器写入有效认证
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return true;
        }
        writeUnauthorized(response);
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, "未登录，请先登录")));
    }
}
