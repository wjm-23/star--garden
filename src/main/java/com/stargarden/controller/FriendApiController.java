package com.stargarden.controller;

import com.stargarden.entity.FriendLike;
import com.stargarden.entity.User;
import com.stargarden.repository.FriendLikeRepository;
import com.stargarden.service.GardenService;
import com.stargarden.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Vue3 SPA 需要的好友互访 / 点赞 API（JwtInterceptor 保护）。
 * 好友搜索返回对方的花园地图 + 统计，点赞同一用户每天限一次。
 */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendApiController {

    private final UserService userService;
    private final GardenService gardenService;
    private final FriendLikeRepository friendLikeRepository;

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String username, HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("currentUserId");
        Map<String, Object> res = new HashMap<>();
        if (uid == null) { res.put("success", false); res.put("msg", "未登录"); return res; }
        if (username == null || username.isBlank()) { res.put("success", false); res.put("msg", "请输入用户名"); return res; }
        User friend = userService.findByUsername(username.trim()).orElse(null);
        if (friend == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }
        if (friend.getId().equals(uid)) { res.put("success", false); res.put("msg", "不能搜索自己哦"); return res; }

        Map<String, Object> friendMap = new HashMap<>();
        friendMap.put("id", friend.getId());
        friendMap.put("username", friend.getUsername());
        friendMap.put("nickname", friend.getNickname() == null ? friend.getUsername() : friend.getNickname());
        friendMap.put("totalPlants", friend.getTotalPlants() == null ? 0 : friend.getTotalPlants());
        friendMap.put("consecutiveDays", friend.getConsecutiveDays() == null ? 0 : friend.getConsecutiveDays());
        friendMap.put("gardenSize", friend.getGardenSize() == null ? 6 : friend.getGardenSize());

        // 对方花园地图（含生长阶段）
        Map<String, Object> garden = gardenService.getUserGardenMap(friend);

        // 是否已点赞（今天）
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        boolean likedToday = friendLikeRepository.existsByFromUserIdAndToUserIdAndGardenViewTimeAfter(
                uid, friend.getId(), todayStart);
        int likeCount = friendLikeRepository.countByToUserId(friend.getId());

        res.put("success", true);
        res.put("friend", friendMap);
        res.put("garden", garden);
        res.put("hasLikedToday", likedToday);
        res.put("likeCount", likeCount);
        return res;
    }

    @PostMapping("/like")
    public Map<String, Object> like(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("currentUserId");
        Map<String, Object> res = new HashMap<>();
        if (uid == null) { res.put("success", false); res.put("msg", "未登录"); return res; }
        Long friendId = body.get("friendId");
        if (friendId == null || friendId.equals(uid)) { res.put("success", false); res.put("msg", "参数错误"); return res; }

        User friend = userService.findById(friendId).orElse(null);
        if (friend == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        if (friendLikeRepository.existsByFromUserIdAndToUserIdAndGardenViewTimeAfter(uid, friendId, todayStart)) {
            res.put("success", false);
            res.put("msg", "今天已经点过赞啦");
            res.put("newCount", friendLikeRepository.countByToUserId(friendId));
            return res;
        }
        FriendLike like = new FriendLike();
        like.setFromUserId(uid);
        like.setToUserId(friendId);
        like.setGardenViewTime(LocalDateTime.now());
        friendLikeRepository.save(like);

        // 成就检查：点赞者 + 被赞者
        User liker = userService.findById(uid).orElse(null);
        if (liker != null) gardenService.checkLikeAchievement(liker);
        gardenService.checkLikeAchievement(friend);

        res.put("success", true);
        res.put("newCount", friendLikeRepository.countByToUserId(friendId));
        return res;
    }
}
