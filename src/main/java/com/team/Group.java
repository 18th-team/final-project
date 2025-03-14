package com.team;

import jakarta.persistence.*;

@Entity
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // 소모임 제목

    @Column(columnDefinition = "TEXT") // 긴 문자열 저장
    private String keywords; // 키워드 (예: "강남,운동,취미")

    // 생성자, Getter, Setter
    public Group() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
}
