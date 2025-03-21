package com.team.moim.entity;

public enum AgeRestriction {
    ABOVE_20("20세 이상"), ABOVE_25("25세 이상"), ABOVE_30("30세 이상"), ABOVE_35("35세 이상"), ABOVE_40("40세 이상"),
    ANYONE("누구나");

    private final String ageKoreanName;

    AgeRestriction(String ageKoreanName) {
        this.ageKoreanName = ageKoreanName;
    }

    public String getAgeKoreanName() {
        return ageKoreanName;
    }
}
