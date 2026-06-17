package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;
import org.sfl.spotifybackendnew.Enums.MessageType;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;
import org.sfl.spotifybackendnew.Services.Messages.MessagingService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class PartySession {
    @Getter
    private final String partyId;
    @Getter
    private PartySettings partySettings;

    private final Map<UUID, PartyUser> userMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UUID> joinOrder = new CopyOnWriteArrayList<>();

    private final SmartQueue queue = new SmartQueue(userMap, joinOrder);
    private PartyPlayer partyPlayer;

    private final MessagingService messagingService;

    private final Object userMapLock = new Object();

    public PartySession(String partyId, PartySettings partySettings, MessagingService messagingService) {
        this.partyId = partyId;
        this.partySettings = partySettings;
        this.messagingService = messagingService;
    }

    public void addUser(UserProfile profile, UserData user) {
        synchronized (userMapLock) {
            UUID userId = user.getUserId();
            if (isUserInParty(userId)) {
                log.info("User {} is already in party with id {}, skipping add", userId, partyId);
                return;
            }
            List<Track> oldUserQueue = deleteDuplicateUser(profile, userId);
            PartyUser partyUser;
            if (oldUserQueue != null)
                partyUser = new PartyUser(userId, profile, user, oldUserQueue);
            else
                partyUser = new PartyUser(userId, profile, user);
            userMap.put(userId, partyUser);
            joinOrder.add(userId);
            log.info("Adding user {} to party with id {}", userId, partyId);
            log.info("Current users in party {}: {} ({})", partyId, userMap.keySet(), userMap.size());
        }
    }
    public void removeUser(UUID userId) {
        synchronized (userMapLock) {
            if (partyPlayer != null && partyPlayer.getPlayerId().equals(userId)) {
                log.info("Player {} left the party, clearing player", userId);
                clearPlayer();
            }
            userMap.remove(userId);
            joinOrder.remove(userId);
            log.info("Removed user {} from party with id {}", userId, partyId);
            log.info("Current users in party {}: {} ({})", partyId, userMap.keySet(), userMap.size());

        }
    }
    public boolean isUserInParty(UUID userId) {
        return userMap.containsKey(userId);
    }
    public void updateUser(UUID userId, UserProfile profile, UserData userSession) {
        synchronized (userMapLock) {
            PartyUser user = userMap.get(userId);

            if (user != null) {
                user.setProfile(profile);
                user.setUserSession(userSession);
            }

            deleteDuplicateUser(profile, userId);
        }
    }

    public void initializePlayer(PartyPlayer player) {
        //check if player is already initialized, if so clear it first
        if (partyPlayer != null && partyPlayer.getPlayerId() != player.getPlayerId()) {
            log.info("Player already initialized for party {}, clearing existing player before initializing new one", partyId);
            partyPlayer.decouplePlayerSession();
            clearPlayer();
        }

        player.setPartyQueue(queue);
        partyPlayer = player;
    }
    public void clearPlayer() {
        partyPlayer = null;
    }

    public boolean playNext() {
        PartyPlayer player = this.partyPlayer; // thread safe read
        if (player == null) return false;
        return player.playNextTrack(false);
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
            messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
        }
    }

    public List<AddedTrack> getPartyQueue() {
        return queue.getQueue();
    }
    public AddedTrack getCurrentlyPlaying() {
        return partyPlayer.getCurrentlyPlaying();
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

    public int voteForSkip(UUID userId) {
        PartyPlayer player = this.partyPlayer; // thread safe read
        if (player == null) return 0;
        return player.voteForSkip(userId);
    }
    public int cancelUserSkipVote(UUID userId) {
        PartyPlayer player = this.partyPlayer; // thread safe read
        if (player == null) return 0;
        return player.cancelUserSkipVote(userId);
    }
    public int getTotalUsers() {
        synchronized (userMapLock) {
            return userMap.size();
        }
    }

    private PartyUser getPartyUser(UUID userId) {
        return userMap.get(userId);
    }
    private List<Track> deleteDuplicateUser(UserProfile profile, UUID validUserId) {
        if (profile.spotifyAuthorized()) {
            for (PartyUser user : userMap.values()) {
                if (user.getId() == validUserId) continue;
                UserProfile userProfile = user.getProfile();
                if (userProfile.spotifyAuthorized() && Objects.equals(userProfile.spotifyId(), profile.spotifyId())) {
                    List<Track> oldQueue = user.getQueue();
                    removeUser(user.getId());
                    UserData userSession = user.getUserSession();
                    userSession.setPartyId(null);
                    userSession.setUser(false);
                    userSession.setPlayer(false);
                    userSession.setHost(false);
                    messagingService.sendPrivateUpdate(user.getId(), MessageType.REFRESH_STATUS);
                    log.info("Removed duplicate spotify user");
                    return oldQueue;
                }
            }
        }
        return null;
    }
}
