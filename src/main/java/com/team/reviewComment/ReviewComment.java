package com.team.reviewComment;

import com.team.feedPost.FeedPost;
import com.team.reviewPost.ReviewPost;
import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createDate;

    @ManyToOne
    private SiteUser author;

    @ManyToOne
    private ReviewPost reviewPost;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private ReviewComment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<ReviewComment> children = new ArrayList<>();

}
