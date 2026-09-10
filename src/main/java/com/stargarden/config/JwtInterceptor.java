package com.stargarden.config;

import com.stargarden.entity.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RESTful API 认证拦截器（/api/**）。
 * 双通道认证：
 * 1) 页面会话（Session）：服务端渲染页面登录后可无缝调用 API；
 * 2) JWT：前后端分离 / 移动端通过 Authorization: Bearer &lt;token&gt; 调用。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 通道1：页面会话
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            User u = (User) session.getAttribute("user");
            request.setAttribute("currentUserId", u.getId());
            return true;
        }
        // 通道2：JWT
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Claims claims = jwtUtil.parse(auth.substring(7));
            if (claims != null) {
                request.setAttribute("currentUserId", Long.valueOf(claims.getSubject()));
                return true;
            }
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"msg\":\"未登录或token无效\"}");
        return false;
    }
}
