package com.stargarden.controller;

import com.stargarden.config.JwtUtil;
import com.stargarden.entity.User;
import com.stargarden.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RESTful 认证接口：/api/auth/**
 * 登录 / 注册成功签发 JWT，客户端后续携带 Authorization: Bearer &lt;token&gt; 调用受保护接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /** 用户名密码登录，成功返回 JWT */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        Map<String, Object> res = new HashMap<>();
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        Optional<User> opt = userService.verifyLogin(username, password);
        if (opt.isEmpty()) {
            res.put("success", false);
            res.put("msg", "用户名或密码错误");
            return res;
        }
        User user = opt.get();
        if (!user.isEnabled()) {
            res.put("success", false);
            res.put("msg", "账号已被禁用");
            return res;
        }
        return tokenResponse(user);
    }

    /** 注册并直接登录（返回 JWT） */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        Map<String, Object> res = new HashMap<>();
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        String email = body.getOrDefault("email", "").trim();
        String nickname = body.getOrDefault("nickname", "").trim();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            res.put("success", false);
            res.put("msg", "用户名、密码与邮箱不能为空");
            return res;
        }
        if (password.length() < 6) {
            res.put("success", false);
            res.put("msg", "密码长度至少 6 位");
            return res;
        }
        if (userService.existsByUsername(username)) {
            res.put("success", false);
            res.put("msg", "用户名已存在");
            return res;
        }
        if (userService.existsByEmail(email)) {
            res.put("success", false);
            res.put("msg", "邮箱已被注册");
            return res;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(password); // register 内部做 BCrypt 加密
        user.setEmail(email);
        user.setNickname(nickname.isEmpty() ? username : nickname);
        user.setCreateTime(LocalDateTime.now());
        user = userService.register(user);
        return tokenResponse(user);
    }

    /** 已有页面会话的用户换取 JWT（服务端渲染页与前后端分离并存的过渡约定） */
    @GetMapping("/token")
    public Map<String, Object> token(HttpSession session) {
        User user = (User) session.getAttribute("user");
        Map<String, Object> res = new HashMap<>();
        if (user == null) {
            res.put("success", false);
            res.put("msg", "请先登录");
            return res;
        }
        return tokenResponse(user);
    }

    private Map<String, Object> tokenResponse(User user) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("token", jwtUtil.generate(user.getId(), user.getUsername(), user.getRole()));
        res.put("tokenType", "Bearer");
        res.put("expiresIn", 86400);
        res.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname() == null ? user.getUsername() : user.getNickname(),
                "role", user.getRole()));
        return res;
    }
}
