package com.team.reviewPost;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewPostForm {

    @NotEmpty(message="제목은 필수 입력 항목입니다.")
    @Size(max=100)
    private String title;

    @NotEmpty(message="내용은 필수 입력 항목입니다.")
    private String content;

    private String tags;

    private Long clubID; // 선택된 모임 ID
}
