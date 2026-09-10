package com.stargarden.controller;

import com.stargarden.entity.User;
import com.stargarden.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpSession session, Model model) {
        User user = userService.findByUsername(username).orElse(null);
        if (user == null) {
            model.addAttribute("error", "用户名不存在");
            return "login";
        }
        // 改用 BCrypt 验证（兼容纯明文：如果数据库里还不是 bcrypt 格式，旧账号先明文比对，再升级）
        boolean passOk;
        if (user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$")) {
            passOk = userService.verifyLogin(username, password).isPresent();
        } else {
            passOk = user.getPassword().equals(password);
            if (passOk) {
                // 静默升级为 bcrypt
                user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
                userService.update(user);
                log.info("用户 {} 密码已静默升级为 BCrypt", username);
            }
        }
        if (!passOk) {
            model.addAttribute("error", "密码错误");
            return "login";
        }
        if (!user.isEnabled()) {
            model.addAttribute("error", "账号已被禁用");
            return "login";
        }
        session.setAttribute("user", user);
        log.info("用户登录成功: username={}", username);
        return "redirect:/tasks";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(User user, @RequestParam String confirmPassword, Model model) {
        if (!user.getPassword().equals(confirmPassword)) {
            model.addAttribute("error", "两次密码不一致");
            return "register";
        }
        if (userService.existsByUsername(user.getUsername())) {
            model.addAttribute("error", "用户名已存在");
            return "register";
        }
        if (userService.existsByEmail(user.getEmail())) {
            model.addAttribute("error", "邮箱已被注册");
            return "register";
        }
        user.setCreateTime(LocalDateTime.now());
        userService.register(user);
        log.info("新用户注册: username={}", user.getUsername());
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            log.info("用户退出登录: username={}", user.getUsername());
        }
        session.invalidate();
        return "redirect:/login";
    }
}
