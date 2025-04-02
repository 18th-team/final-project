package com.team.chat;

public class ChatRoomUpdateEvent {
    private final String requesterUuid;
    private final String ownerUuid;

    public ChatRoomUpdateEvent(String requesterUuid, String ownerUuid) {
        this.requesterUuid = requesterUuid;
        this.ownerUuid = ownerUuid;
    }

    public String getRequesterUuid() {
        return requesterUuid;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }
}