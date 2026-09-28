package com.stargarden.controller;

import com.stargarden.entity.User;
import com.stargarden.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 个人资料更新 API。只允许更新 nickname / email / avatarUrl 三个非敏感字段，
 * username / password / role 需走独立的安全流程。
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileApiController {

    private final UserService userService;

    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("currentUserId");
        Map<String, Object> res = new HashMap<>();
        if (uid == null) { res.put("success", false); res.put("msg", "未登录"); return res; }
        User user = userService.findById(uid).orElse(null);
        if (user == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }

        String nickname = body.get("nickname");
        String email = body.get("email");
        String avatarUrl = body.get("avatarUrl");

        if (nickname != null) {
            String trimmed = nickname.trim();
            if (!trimmed.isEmpty()) user.setNickname(trimmed);
        }
        if (email != null) {
            String trimmed = email.trim();
            if (!trimmed.isEmpty()) {
                if (!trimmed.contains("@")) { res.put("success", false); res.put("msg", "邮箱格式不正确"); return res; }
                user.setEmail(trimmed);
            }
        }
        if (avatarUrl != null) {
            String trimmed = avatarUrl.trim();
            user.setAvatarUrl(trimmed.isEmpty() ? null : trimmed);
        }

        User saved = userService.update(user);
        Map<String, Object> userOut = new HashMap<>();
        userOut.put("id", saved.getId());
        userOut.put("username", saved.getUsername());
        userOut.put("nickname", saved.getNickname());
        userOut.put("email", saved.getEmail());
        userOut.put("role", saved.getRole());
        res.put("success", true);
        res.put("user", userOut);
        return res;
    }
}
