package com.team.feedPost;

import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter
@Setter
public class FeedPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postID;

    @Column(length = 100)
    private String title;

    @Column(columnDefinition="TEXT")
    private String content;

    @Column(name="CREATE_DATE")
    private LocalDateTime createDate;

    private String tags; // 맛집,강남,소셜다이닝 -> 이런 식으로 입력

    @ManyToOne
    private SiteUser author;

    @ManyToMany
    private Set<SiteUser> voter;

    private String imageURL;

    /*
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_moim_id")
    private NewMoim moim;

     */


    /*
    @Transient
    public String getMoimTitle() {
        return moim != null ? moim.getTitle() : null;
    }

     */


    @Transient // DB에는 저장하지 않음
    public List<String> getTagList() {
        if (tags == null || tags.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // 공백 제거 후 쉼표로 분리
        String cleanedTags = tags.replaceAll("\\s+", ""); // 모든 공백 제거
        return Arrays.asList(cleanedTags.split(","));
    }
}
