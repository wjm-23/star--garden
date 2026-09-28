package com.stargarden.repository;

import com.stargarden.entity.FriendLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface FriendLikeRepository extends JpaRepository<FriendLike, Long> {
    boolean existsByFromUserIdAndToUserIdAndGardenViewTimeAfter(Long fromUserId, Long toUserId, LocalDateTime time);
    int countByToUserId(Long toUserId);
    int countByFromUserId(Long fromUserId);

    /** 管理端删除用户时级联清理其发出的点赞 */
    void deleteByFromUserId(Long fromUserId);

    /** 管理端删除用户时级联清理其收到的点赞 */
    void deleteByToUserId(Long toUserId);
}