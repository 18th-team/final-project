package com.team.comment;

import com.team.post.Post;
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
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Post post; // 댓글이 달린 게시글

    @ManyToOne
    private SiteUser author;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    private Comment parent; // 댓글일 경우: null, 답댓글일 경우: 원댓글

    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE)
    private List<Comment> children = new ArrayList<>();

    private LocalDateTime createDate; // 댓글 작성일
    private LocalDateTime modifyDate; // 댓글 수정일
}