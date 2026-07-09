package org.sfl.spotifybackendnew.Objects.Party;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;

import java.util.*;

@Data
public class PartyUser {
    private final UUID id;
    private UserData userSession;

    // display info
    private UserProfile profile;

    @Getter(AccessLevel.NONE)
    private final LinkedHashMap<UUID, Track> queue = new LinkedHashMap<>();

    public PartyUser(UUID userId, UserProfile profile, UserData userSession) {
        id = userId;
        this.profile = profile;
        this.userSession = userSession;
    }
    public PartyUser(UUID userId, UserProfile profile, UserData userSession, Map<UUID, Track> queue) {
        id = userId;
        this.profile = profile;
        this.userSession = userSession;
        this.queue.putAll(queue);
    }

    public synchronized void addTrack(Track track) {
        queue.put(UUID.randomUUID(), track);
    }
    public synchronized void removeTrack(UUID queueItemId) {
        queue.remove(queueItemId);
    }
    public synchronized void removeFirstTrack() {
        queue.pollFirstEntry();
    }
    public synchronized LinkedHashMap<UUID, Track> getQueue() {
        return new LinkedHashMap<>(queue);
    }
}