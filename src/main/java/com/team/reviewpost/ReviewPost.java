package com.team.reviewpost;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    private String tags; // 맛집,강남,소셜다이닝 -> 이런 식으로 입력

    private String imageURL;

    /*
    @ManyToOne
    @JoinColumn(name = "moim_id")
    private Moim moim; // 참여한 소모임
    */

    @Transient // DB에는 저장하지 않음
    public List<String> getTagList() {
        if(tags == null || tags.equals("")) {
            return Collections.emptyList();
        }
        return Arrays.asList(tags.split(","));
    }
}
