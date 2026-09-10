package com.stargarden.controller;

import com.stargarden.ws.FocusRoomEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 协作专注房间列表接口：GET /api/rooms
 * 返回当前活跃房间及成员数，供 rooms 页面"加入房间"展示。
 */
@RestController
@RequestMapping("/api")
public class RoomApiController {

    @GetMapping("/rooms")
    public Map<String, Object> rooms() {
        List<Map<String, Object>> active = FocusRoomEndpoint.activeRooms();
        return Map.of("success", true, "rooms", active);
    }
}
