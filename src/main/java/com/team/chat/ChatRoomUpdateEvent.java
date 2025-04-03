package com.team.chat;

import java.util.HashSet;
import java.util.Set;

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

    public Set<String> getAffectedUuids() {
        return affectedUuids;
    }
}