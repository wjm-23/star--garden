package com.stargarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_garden")
public class UserGarden {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long plantId;
    private Integer positionX;
    private Integer positionY;
    private LocalDateTime plantTime;

    /**
     * 在格子内「浇水培育」额外累加的成长经验（分钟）。
     * 与完成任务获得的同植物专注分钟相加，共同决定该植株的生长阶段。
     * 默认 0，仅作为花园内的互动养育手段，不影响专注历史/成就统计。
     */
    private Integer growthBonus = 0;
}