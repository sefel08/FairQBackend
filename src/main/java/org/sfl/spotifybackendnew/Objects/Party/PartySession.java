package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class PartySession {
    @Getter
    private final String partyId;

    private final Map<UUID, PartyUser> userMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UUID> joinOrder = new CopyOnWriteArrayList<>();

    private final SmartQueue queue = new SmartQueue(userMap, joinOrder);
    private PartyPlayer partyPlayer;

    private final Object userMapLock = new Object();

    public PartySession(String partyId) {
        this.partyId = partyId;
    }

    public void addUser(UUID userId, UserProfile profile) {
        synchronized (userMapLock) {
            if (userMap.containsKey(userId))
                return;

            deleteDuplicateUser(profile, userId);

            PartyUser user = new PartyUser(userId, profile);
            userMap.put(userId, user);
            joinOrder.add(userId);

            log.info("Adding user {} to party with id {}", userId, partyId);
            log.info("Current users in party {}: {} ({})", partyId, userMap.keySet(), userMap.size());
        }
    }
    public void removeUser(UUID userId) {
        synchronized (userMapLock) {
            if (userMap.containsKey(userId)) {
                userMap.remove(userId);
                joinOrder.remove(userId);
                log.info("Removed user {} from party with id {}", userId, partyId);
                log.info("Current users in party {}: {} ({})", partyId, userMap.keySet(), userMap.size());
            }
        }
    }
    public void updateUser(UUID userId, UserProfile profile) {
        synchronized (userMapLock) {
            if (userMap.containsKey(userId)) {
                userMap.get(userId).setProfile(profile);
            }

            deleteDuplicateUser(profile, userId);
        }
    }

    public void initializePlayer(PartyPlayer player) {
        player.setPartyQueue(queue);
        partyPlayer = player;
    }
    public void clearPlayer() {
        partyPlayer = null;
    }

    public boolean playNext() {
        PartyPlayer player = this.partyPlayer; // thread safe read
        if (partyPlayer == null) return false;
        return partyPlayer.playNextTrack();
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
            synchronized (this) {
                user.addTrack(track);
                if (partyPlayer != null) {
                    partyPlayer.notifyNewTrackAdded();
                }
            }
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
    public List<UserProfile> getPartyUsers() {
        List<UserProfile> users = new ArrayList<>(joinOrder.size());

        for (UUID userId : joinOrder) {
            PartyUser user = userMap.get(userId);
            if (user != null) {
                users.add(user.getProfile());
            }
        }

        return users;
    }

    private PartyUser getPartyUser(UUID userId) {
        return userMap.get(userId);
    }
    private void deleteDuplicateUser(UserProfile profile, UUID validUserId) {
        if (profile.spotifyAuthorized()) {
            for (PartyUser user : userMap.values()) {
                if (user.getId() == validUserId) continue;
                UserProfile userProfile = user.getProfile();
                if (userProfile.spotifyAuthorized() && Objects.equals(userProfile.spotifyId(), profile.spotifyId())) {
                    userMap.remove(user.getId());
                    joinOrder.remove(user.getId());
                    log.info("Removed duplicate spotify user {} from party with id {}", profile.spotifyId(), partyId);
                    break;
                }
            }
        }
    }
}
