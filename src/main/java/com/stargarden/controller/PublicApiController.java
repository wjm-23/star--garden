package com.stargarden.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 公共入口：/api/ 根路径健康检查（无需鉴权） */
@RestController
@RequestMapping("/api")
public class PublicApiController {

    @GetMapping({"/", ""})
    public Map<String, Object> root() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("service", "星芽花园 Star Garden API");
        r.put("version", "1.0.0");
        r.put("status", "UP");
        r.put("time", LocalDateTime.now().toString());
        r.put("endpoints", new String[]{
                "POST /api/auth/login       登录",
                "POST /api/auth/register    注册",
                "GET  /api/user/profile     当前用户资料",
                "GET  /api/tasks            任务大厅",
                "POST /api/focus/start      开始专注",
                "POST /api/focus/complete   完成专注",
                "GET  /api/garden/map       花园地图",
                "POST /api/garden/plant     种植",
                "POST /api/garden/remove    铲除",
                "POST /api/garden/grow     同格再种(格子内成长)",
                "POST /api/garden/expand    扩建",
                "GET  /api/garden/pending   待种种子",
                "GET  /api/history          专注历史",
                "GET  /api/report/overview  数据报告",
                "GET  /api/achievements     成就",
                "GET  /api/encyclopedia     植物图鉴",
                "GET  /api/recommend        混合推荐",
                "GET  /api/friends/search   搜索好友",
                "POST /api/friends/like     点赞好友花园",
                "WS   /ws/focus-room        协作房间 WebSocket"
        });
        return r;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "UP");
        r.put("time", LocalDateTime.now().toString());
        return r;
    }
}
