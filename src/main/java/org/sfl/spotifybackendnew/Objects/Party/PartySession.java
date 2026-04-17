package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class PartySession {
    @Getter
    private final String partyId;

    private final Map<UUID, PartyUser> userMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UUID> joinOrder = new CopyOnWriteArrayList<>();

    private final SmartQueue queue = new SmartQueue(userMap, joinOrder);

    public PartySession(String partyId) {
        this.partyId = partyId;
    }

    public void addUser(UUID userId, UserProfile profile) {
        if (userMap.containsKey(userId))
            return;

        PartyUser user = new PartyUser(userId, profile);
        userMap.put(userId, user);
        joinOrder.add(userId);

        log.info("Adding user {} to party with id {}", userId, partyId);
        log.info("Current users in party {}: {} ({})", partyId, userMap.keySet(), userMap.size());
    }
    public void removeUser(UUID userId) {
        if (userMap.containsKey(userId)) {
            userMap.remove(userId);
            joinOrder.remove(userId);
            log.info("Removed user {} from party with id {}", userId, partyId);
            log.info("Current users in party {}: {} ({})", partyId, userMap.keySet(), userMap.size());
        }
    }

    public List<Track> getUserQueue(UUID userId) {
        PartyUser user = getPartyUser(userId);
        if (user != null) {
            return user.getQueue();
        }
        return List.of();
    }
    public void addToUserQueue(UUID userId, Track track) {
        PartyUser user = getPartyUser(userId);
        if (user != null) {
            user.addTrack(track);
        }
    }
    public void removeFromUserQueue(UUID userId, int index) {
        PartyUser user = getPartyUser(userId);
        if (user != null) {
            user.removeTrack(index);
        }
    }

    public List<AddedTrack> getPartyQueue() {
        return queue.getQueue();
    }

    private PartyUser getPartyUser(UUID userId) {
        return userMap.get(userId);
    }
}
