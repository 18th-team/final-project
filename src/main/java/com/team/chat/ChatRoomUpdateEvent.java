package com.team.chat;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public class ChatRoomUpdateEvent {
    private Set<String> affectedUuids;

    public ChatRoomUpdateEvent(Set<String> affectedUuids) {
        this.affectedUuids = affectedUuids;
    }

    public ChatRoomUpdateEvent(String requesterUuid, String ownerUuid) {
        this.affectedUuids = new HashSet<>();
        this.affectedUuids.add(requesterUuid);
        this.affectedUuids.add(ownerUuid);
    }

}