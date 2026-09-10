package com.stargarden.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stargarden.entity.TaskRecord;
import com.stargarden.service.TaskCategoryUtil;
import com.stargarden.config.SpringContextHolder;
import com.stargarden.repository.TaskRecordRepository;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 协作专注房间 WebSocket 端点：/ws/room/{roomId}?uid=&uname=
 * 消息协议（JSON）：
 *   客户端上行：{"type":"focus-start","minutes":25} / {"type":"focus-end"} / {"type":"chat","msg":".."} / {"type":"pk"}
 *   服务端下行：{"type":"system"|"focus-start"|"focus-end"|"chat"|"pk", ...,"members":[{name,focusing}]}
 * 好友 PK：房间成员今日累计专注分钟数实时排行。
 */
@Component
@ServerEndpoint("/ws/room/{roomId}")
public class FocusRoomEndpoint {

    /** roomId -> 房间成员会话集合 */
    private static final Map<String, Set<Session>> ROOMS = new ConcurrentHashMap<>();
    /** websocket sessionId -> 成员信息 {uid, uname} */
    private static final Map<String, Map<String, Object>> MEMBERS = new ConcurrentHashMap<>();
    /** websocket sessionId -> 是否专注中 */
    private static final Map<String, Boolean> FOCUSING = new ConcurrentHashMap<>();
    /** websocket sessionId -> 专注开始时间戳（毫秒），服务端计时防客户端伪造 */
    private static final Map<String, Long> FOCUS_START = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String roomId;

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) throws Exception {
        this.roomId = roomId;
        Map<String, List<String>> params = session.getRequestParameterMap();
        Map<String, Object> member = new HashMap<>();
        member.put("uid", Long.parseLong(params.getOrDefault("uid", List.of("0")).get(0)));
        member.put("uname", params.getOrDefault("uname", List.of("访客")).get(0));
        MEMBERS.put(session.getId(), member);
        FOCUSING.put(session.getId(), false);
        ROOMS.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
        broadcast(roomId, Map.of("type", "system",
                "msg", member.get("uname") + " 进入了房间",
                "members", members(roomId)));
    }

    @OnMessage
    public void onMessage(Session session, String message) throws Exception {
        Map<?, ?> m = MAPPER.readValue(message, Map.class);
        String type = String.valueOf(m.get("type"));
        Map<String, Object> member = MEMBERS.getOrDefault(session.getId(), Map.of());
        String uname = String.valueOf(member.getOrDefault("uname", "成员"));
        switch (type) {
            case "focus-start" -> {
                FOCUSING.put(session.getId(), true);
                FOCUS_START.put(session.getId(), System.currentTimeMillis());
                Object minutes = m.get("minutes");
                broadcast(roomId, Map.of("type", "focus-start", "who", uname,
                        "minutes", minutes == null ? 25 : minutes, "members", members(roomId)));
            }
            case "focus-end" -> {
                FOCUSING.put(session.getId(), false);
                Object uidObj = member.get("uid");
                long uid = uidObj instanceof Long l ? l : 0L;
                long minutes = settleFocus(session.getId(), uid);
                broadcast(roomId, Map.of("type", "focus-end", "who", uname,
                        "minutes", minutes, "members", members(roomId)));
                // 专注结束后刷新一次 PK 榜（刚完成的时长已计入今日）
                broadcast(roomId, Map.of("type", "pk", "board", pkBoard(roomId)));
            }
            case "chat" -> broadcast(roomId, Map.of("type", "chat", "who", uname, "msg", String.valueOf(m.get("msg"))));
            case "pk" -> broadcast(roomId, Map.of("type", "pk", "board", pkBoard(roomId)));
            default -> { }
        }
    }

    @OnClose
    public void onClose(Session session) {
        Set<Session> room = ROOMS.get(roomId);
        if (room == null) return;
        room.remove(session);
        Map<String, Object> info = MEMBERS.get(session.getId());
        String uname = String.valueOf(info == null ? "成员" : info.getOrDefault("uname", "成员"));
        // 直接关闭页面/断线的成员：专注中的自动按服务端计时结算，保证时长不丢失
        if (FOCUS_START.containsKey(session.getId()) && info != null) {
            Object uidObj = info.get("uid");
            settleFocus(session.getId(), uidObj instanceof Long l ? l : 0L);
        }
        MEMBERS.remove(session.getId());
        FOCUSING.remove(session.getId());
        if (room.isEmpty()) {
            ROOMS.remove(roomId);
        } else {
            try {
                broadcast(roomId, Map.of("type", "system", "msg", uname + " 离开了房间", "members", members(roomId)));
            } catch (Exception ignored) { }
        }
    }

    @OnError
    public void onError(Session session, Throwable t) {
        // 单连接异常不中断房间
    }

    /** 房间成员实时状态列表 */
    private List<Map<String, Object>> members(String rid) {
        List<Map<String, Object>> list = new ArrayList<>();
        Set<Session> room = ROOMS.get(rid);
        if (room == null) return list;
        for (Session s : room) {
            Map<String, Object> info = MEMBERS.get(s.getId());
            if (info == null) continue;
            Map<String, Object> mm = new HashMap<>();
            mm.put("name", info.get("uname"));
            mm.put("focusing", FOCUSING.getOrDefault(s.getId(), false));
            list.add(mm);
        }
        return list;
    }

    /** 好友 PK 榜：房间内成员今日累计专注分钟数 */
    private List<Map<String, Object>> pkBoard(String rid) {
        TaskRecordRepository repo = SpringContextHolder.getBean(TaskRecordRepository.class);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> board = new ArrayList<>();
        Set<Session> room = ROOMS.get(rid);
        if (room == null) return board;
        for (Session s : room) {
            Map<String, Object> info = MEMBERS.get(s.getId());
            if (info == null) continue;
            long uid = info.get("uid") instanceof Long l ? l : 0L;
            Long minutes = repo.sumDurationByUserAndDay(uid, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            Map<String, Object> row = new HashMap<>();
            row.put("name", info.get("uname"));
            row.put("minutes", minutes == null ? 0 : minutes);
            board.add(row);
        }
        board.sort((a, b) -> Long.compare(((Number) b.get("minutes")).longValue(),
                ((Number) a.get("minutes")).longValue()));
        return board;
    }

    private void broadcast(String rid, Map<String, Object> payload) throws Exception {
        String json = MAPPER.writeValueAsString(payload);
        Set<Session> room = ROOMS.get(rid);
        if (room == null) return;
        for (Session s : room) {
            if (s.isOpen()) {
                synchronized (s) {
                    s.getBasicRemote().sendText(json);
                }
            }
        }
    }

    /**
     * 服务端结算专注时长并写入任务记录：
     * 以服务端时钟差计算实际专注分钟数（最低按 1 分钟计），防止客户端伪造时长；
     * 结算失败的成员不中断房间通信。
     */
    private long settleFocus(String wsSessionId, long uid) {
        Long start = FOCUS_START.remove(wsSessionId);
        if (start == null || uid <= 0) return 0;
        long minutes = Math.max(1, (System.currentTimeMillis() - start) / 60000);
        try {
            TaskRecordRepository repo = SpringContextHolder.getBean(TaskRecordRepository.class);
            TaskRecord record = new TaskRecord();
            record.setUserId(uid);
            record.setTaskName("协作房间专注");
            record.setTaskCategory(TaskCategoryUtil.derive("协作房间专注"));
            record.setDurationMinutes((int) minutes);
            record.setCompletedTime(java.time.LocalDateTime.now());
            record.setPlantId(6L); // 星芽草：协作专注的默认奖励植物
            record.setPlanted(false);
            repo.save(record);
        } catch (Exception ignored) { }
        return minutes;
    }

    /** 当前活跃房间列表（供 /api/rooms 展示） */
    public static List<Map<String, Object>> activeRooms() {
        List<Map<String, Object>> list = new ArrayList<>();
        ROOMS.forEach((rid, sessions) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("roomId", rid);
            row.put("memberCount", sessions.size());
            list.add(row);
        });
        return list;
    }
}
