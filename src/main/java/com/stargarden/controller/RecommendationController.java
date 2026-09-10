package com.stargarden.controller;

import com.stargarden.dto.RecommendationItem;
import com.stargarden.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个性化推荐接口：GET /api/recommendations?n=5
 * 身份由 JwtInterceptor 解析（Session 或 JWT 双通道），从 request 属性 currentUserId 获取。
 */
@RestController
@RequestMapping("/api")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    @GetMapping("/recommendations")
    public Map<String, Object> recommendations(@RequestParam(defaultValue = "5") int n,
                                               HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            res.put("success", false);
            res.put("msg", "未登录");
            return res;
        }
        List<RecommendationItem> items = recommendationService.recommend(userId, Math.min(Math.max(n, 1), 7));
        res.put("success", true);
        res.put("count", items.size());
        res.put("items", items);
        return res;
    }
}
