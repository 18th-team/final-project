package com.team.moim.entity;

public enum MoimTheme {
    FOOD_DRINK("푸드 / 드링크 "), SELF_IMPROVEMENT("자기계발 "), HOBBY("취미"), ACTIVITY("액티비티 "), CULTURE_ART("문화 / 예술"),
    TRAVEL("여행");

    private final String moimThemeKoreanName;

    public String getMoimThemeKoreanName() {
        return moimThemeKoreanName;
    }

    MoimTheme(String moimThemeKoreanName) {
        this.moimThemeKoreanName = moimThemeKoreanName;
    }


}
