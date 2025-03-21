package com.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NewMoim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer minParticipants;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeRestriction ageRestriction;

    @Column(nullable = false)
    private Boolean hasFee;

    @Column
    private Integer feeAmount;

    @ElementCollection
    @CollectionTable(name = "moim_fee_details", joinColumns = @JoinColumn(name = "new_moim_id"))
    @Column(name = "fee_detail")
    @Enumerated(EnumType.STRING)
    private List<FeeDetail> feeDetails = new ArrayList<>();

    @Column(nullable = false)
    private Boolean isOnline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district; // District 엔티티와 관계 설정

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MoimTheme moimTheme;

    @ElementCollection
    @CollectionTable(name = "moim_images", joinColumns = @JoinColumn(name = "new_moim_id"))
    @Column(name = "image_path")
    private List<String> images = new ArrayList<>();

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime time;
}