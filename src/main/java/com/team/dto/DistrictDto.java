package com.team.dto;

public class DistrictDto {
    private Long id;
    private String name;

    public DistrictDto(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}