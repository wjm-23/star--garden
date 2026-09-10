package com.stargarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String nickname;
    private String email;
    private String password;
    private String avatarUrl;
    private String role = "USER";
    private boolean enabled = true;
    private Integer consecutiveDays = 0;
    private Integer totalPlants = 0;
    private LocalDateTime createTime = LocalDateTime.now();
    private Integer gardenSize = 6;  // 花园网格边长：默认 6×6，可扩建至 10×10
}