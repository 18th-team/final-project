package com.team.moim.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "club_file")
public class ClubFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String originalFilename;

    @Column
    private String storedFileName;

    //연결하는 테이블 외래키 Join 설정
    //크루1그룹당 등록되는 이미지가 여러장이니깐,
    // 이미지를 기준으로 다수 : 1 (크루 )
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id") // 이미지 테이블에 적용되는 이름
    private Club club; //조인하려는 엔티티명

    //DTO->엔티티로 바꿔
    public static ClubFileEntity toClubFileEntity(Club club, String originalFilename, String storedFileName) {
        ClubFileEntity clubFileEntity = new ClubFileEntity();
        clubFileEntity.setOriginalFilename(originalFilename);
        clubFileEntity.setStoredFileName(storedFileName);
        clubFileEntity.setClub(club); //pk가 아니라 부모 엔티티를 그대로 저장
        return clubFileEntity;
    }
}
