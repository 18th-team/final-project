package com.team.reviewPost;

import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
public class ReviewPost {
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

    @Transient // DB에는 저장하지 않음
    public List<String> getTagList() {
        if(tags == null || tags.equals("")) {
            return Collections.emptyList();
        }
        return Arrays.asList(tags.split(","));
    }
}
