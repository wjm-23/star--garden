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
}