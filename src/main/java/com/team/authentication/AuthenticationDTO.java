package com.team.authentication;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthenticationDTO {
    private String cellcorp; //통신사
    private String phone; //휴대폰번호
    private String name; //이름
    private String birthDay1; //생년월일
    private String birthDay2;   //성별
}

