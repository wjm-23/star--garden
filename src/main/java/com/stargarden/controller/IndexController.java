package com.stargarden.controller;

import com.stargarden.entity.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

@Controller
public class IndexController {

    @GetMapping("/index")
    public String index(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        return "index";
    }

    // ==================== 数据可视化与协作专注 ====================

    /** 数据报告页（ECharts：趋势/类别/热力日历/黄金时段） */
    @GetMapping("/report")
    public String report(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        return "report";
    }

    /** 年度专注报告大屏 */
    @GetMapping("/annual")
    public String annual(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        return "annual";
    }

    /** 协作专注房间（WebSocket） */
    @GetMapping("/rooms")
    public String rooms(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        return "rooms";
    }
}
