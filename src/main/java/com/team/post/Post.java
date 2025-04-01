package com.team.post;

import com.team.moim.entity.Club;
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
@Table(name = "post")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postID;

    @Column(length = 100)
    private String title;

    @Column(columnDefinition="TEXT")
    private String content;

    @Column(name="CREATE_DATE")
     private LocalDateTime createDate;

    private String tags;

    @ManyToOne
    private SiteUser author;

    @ManyToMany
    private Set<SiteUser> voter;

    private String imageURL;

    @ManyToOne
    private Club club; // Review에만 필요한 경우 nullable로 유지

    @Transient
    public List<String> getTagList() {
        if (tags == null || tags.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(tags.replaceAll("\\s+", "").split(","));
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardType boardType;

    @Transient
    public String getBoardType() {
        return (this.club == null) ? "FEED" : "REVIEW";
    }
}

