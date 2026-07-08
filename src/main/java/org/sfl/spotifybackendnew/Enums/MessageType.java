package org.sfl.spotifybackendnew.Enums;

public enum MessageType {
    // party messages
    PARTY_QUEUE_CHANGED,
    PARTY_USERS_CHANGED,
    USER_QUEUE_CHANGED,
    SKIP_VOTES_CHANGED,

    // private messages
    REFRESH_STATUS,
    REFRESH_TOKEN,
    REFRESH_QUEUE,
}
