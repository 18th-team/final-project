package com.team.moim.entity;

public enum FeeDetail {
    NO_SHOW_PREVENTION("노쇼비"), VENUE_FEE("대관비"), MATERIAL_FEE("재료비   "), SNACK_FEE("간식비"), OTHER("그 외");

    private final String feeDetailKoreanName;

    FeeDetail(String feeDetailKoreanName) {
        this.feeDetailKoreanName = feeDetailKoreanName;
    }

    public String getFeeDetailKoreanName() {
        return feeDetailKoreanName;
    }
}
