package com.team.moim.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Table(name = "keyword")
@NoArgsConstructor
@AllArgsConstructor
public class Keyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // "액티비티", "자기계발" 등
}
