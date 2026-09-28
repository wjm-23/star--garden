package com.stargarden.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

/**
 * SPA 单页应用路由回退控制器。
 * Vue3 前端使用 createWebHistory()（HTML5 路径模式），
 * 当浏览器直接访问 /tasks、/garden 等前端路由时，Spring Boot 需要返回 index.html，
 * 让前端 Vue Router 接管路由匹配，否则会报 404。
 * <p>
 * 规则：
 *   - /api/**   → REST API（由 JwtInterceptor 处理），不进这里
 *   - /ws/**    → WebSocket，不进这里
 *   - /assets/** → Vite 构建产物（JS/CSS/图片），Spring Boot 静态资源处理器直接返回
 *   - 其他路径  → 回退返回 index.html，由 Vue Router 接管
 */
@Controller
public class SpaFallbackController {

    /**
     * 匹配所有非 API/非静态资源的 GET 请求，返回 index.html。
     * 使用 produces=TEXT_HTML 确保浏览器正确识别。
     */
    @RequestMapping(value = { "/", "/login", "/register", "/tasks", "/garden", "/report",
            "/history", "/encyclopedia", "/achievements", "/friends", "/rooms",
            "/annual", "/profile", "/admin/eval", "/admin/tasks", "/admin/users" },
            produces = MediaType.TEXT_HTML_VALUE)
    public String forwardHome() {
        return "forward:/index.html";
    }

    /** 兜底：捕获更深层的路径（如 /friends/xxx）；排除 swagger 前缀，避免吞掉接口文档入口 */
    @RequestMapping(value = "/{path:(?!swagger-ui$|v3$|api-docs$)[^\\.]*}", produces = MediaType.TEXT_HTML_VALUE)
    public String forwardDeep() {
        return "forward:/index.html";
    }

    /** 防止 favicon.ico 404（Vite 构建里可能没包含） */
    @ResponseBody
    @RequestMapping(value = "/favicon.ico")
    public byte[] emptyFavicon() {
        return new byte[0];
    }
}
