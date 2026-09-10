package com.stargarden.repository;

import com.stargarden.entity.FriendLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface FriendLikeRepository extends JpaRepository<FriendLike, Long> {
    boolean existsByFromUserIdAndToUserIdAndGardenViewTimeAfter(Long fromUserId, Long toUserId, LocalDateTime time);
    int countByToUserId(Long toUserId);
}