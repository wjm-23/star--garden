package com.stargarden.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public String handleRuntimeException(RuntimeException e,
                                         HttpServletRequest request,
                                         RedirectAttributes redirectAttributes,
                                         Model model) {
        log.error("运行时异常: {} | 请求路径: {}", e.getMessage(), request.getRequestURI(), e);
        String uri = request.getRequestURI();
        // AJAX 请求由另一个处理器处理（Content-Type判断）
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return null;
        }
        if (uri.startsWith("/admin")) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/users";
        }
        model.addAttribute("error", "服务器异常，请稍后重试：" + e.getMessage());
        return "forward:/index";
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Map<String, Object> handleException(Exception e, HttpServletRequest request) {
        log.error("全局异常: {} | 请求路径: {}", e.getMessage(), request.getRequestURI(), e);
        Map<String, Object> res = new HashMap<>();
        res.put("success", false);
        res.put("msg", "服务器内部错误：" + e.getMessage());
        return res;
    }
}
