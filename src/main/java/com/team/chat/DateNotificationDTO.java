package com.team.chat;

import lombok.Data;

@Data
public class DateNotificationDTO {
    private String date;

    public DateNotificationDTO(String date) {
        this.date = date;
    }
}