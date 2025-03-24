package com.team.feedComment;

import com.team.feedPost.FeedPost;
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
public class FeedComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createDate;

    @ManyToOne
    private SiteUser author;

    @ManyToOne
    private FeedPost feedPost;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private FeedComment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<FeedComment> children = new ArrayList<>();

}
