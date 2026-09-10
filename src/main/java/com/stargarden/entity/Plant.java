package com.stargarden.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "plant")
public class Plant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String imageUrl;
    private String rarity;
    private String icon;
}