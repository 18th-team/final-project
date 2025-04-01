package com.team.post;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PostDTO {
    private Long postID;
    private String title;
    private String content;
    private String imageURL;
    private String tags;
    private String authorName;
    private String authorProfileImage;
    private String boardType;
    private String createDate;

    public static PostDTO from(Post post) {
        return new PostDTO(
                post.getPostID(),
                post.getTitle(),
                post.getContent(),
                post.getImageURL(),
                post.getTags(),
                post.getAuthor().getName(),
                post.getAuthor().getProfileImage(),
                post.getBoardType().toString(),
                post.getCreateDate().toString()
        );
    }
}
