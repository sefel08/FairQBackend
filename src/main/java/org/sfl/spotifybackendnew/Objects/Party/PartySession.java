package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;
import org.sfl.spotifybackendnew.DTOs.User.UserWithId;
import org.sfl.spotifybackendnew.Enums.MessageType;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;
import org.sfl.spotifybackendnew.Services.Messages.MessagingService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyProxyService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PartySession {
    @Getter
    private final String partyId;
    @Getter
    private final UserProfile ownerProfile;
    @Getter
    private PartySettings partySettings;

    private final Map<UUID, PartyUser> userMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UUID> joinOrder = new CopyOnWriteArrayList<>();

    @Getter
    private int totalFallbackTracks;
    private int fallbackIndex = 0;
    private final List<Integer> fallbackTrackIds;
    private final AtomicReference<CompletableFuture<Track>> cachedFallbackTrackFuture = new AtomicReference<>();

    private final SmartQueue queue = new SmartQueue(userMap, joinOrder);
    private PartyPlayer partyPlayer;

    private final MessagingService messagingService;
    private final SpotifyProxyService spotifyProxyService;

    private final Object userMapLock = new Object();

    public PartySession(UserData owner, String userToken, PartySettings partySettings, int totalFallbackPlaylistTracks, MessagingService messagingService, SpotifyProxyService spotifyProxyService) {
        this.partyId = owner.getSpotifyId();
        this.ownerProfile = new UserProfile(owner.getDisplayName(), owner.isSpotifyAuthenticated(), owner.getSpotifyId(), owner.getImageUrl(), owner.getSmallImageUrl());
        this.partySettings = partySettings;
        this.totalFallbackTracks = totalFallbackPlaylistTracks;
        this.messagingService = messagingService;
        this.spotifyProxyService = spotifyProxyService;

        // fallback playlist initialization
        if (totalFallbackTracks != -1) {
            //create random order of ids
            fallbackTrackIds = new ArrayList<>(totalFallbackPlaylistTracks);
            for (int i = 0; i < totalFallbackTracks; i++) {
                fallbackTrackIds.add(i);
            }
            Collections.shuffle(fallbackTrackIds);
        } else  {
            fallbackTrackIds = Collections.emptyList();
        }
        triggerFallbackTrackReplenishment(userToken);
    }

    public void addUser(UserProfile profile, UserData user) {
        synchronized (userMapLock) {
            UUID userId = user.getUserId();
            if (isUserInParty(userId)) {
                log.info("User {} is already in party with id {}, skipping add", userId, partyId);
                return;
            }
            Map<UUID, Track> oldUserQueue = deleteDuplicateUser(profile, userId);
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
    public boolean removeUser(UUID userId) {
        synchronized (userMapLock) {
            if (partyPlayer != null && partyPlayer.getPlayerId().equals(userId)) {
                log.info("Player {} left the party, clearing player", userId);
                clearPlayer();
                messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
            }
            PartyUser user = userMap.remove(userId);
            if (user != null) {
                joinOrder.remove(userId);
                UserData userSession = user.getUserSession();
                userSession.clearRoles();
                userSession.setPartyId(null);
                messagingService.sendPrivateUpdate(userId, MessageType.REFRESH_STATUS);
                messagingService.sendUpdate(partyId, MessageType.PARTY_USERS_CHANGED);
                log.info("Removed user {} from party with id {}", userId, partyId);
                log.info("Current users in party {}: {} ({})", partyId, userMap.keySet(), userMap.size());
                return true;
            } else {
                log.info("User {} was not found in party with id {} in removing process", userId, partyId);
                return false;
            }
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

    public Map<UUID, Track> getUserQueue(UUID userId) {
        PartyUser user = getPartyUser(userId);
        if (user != null) {
            return user.getQueue();
        }
        return Map.of();
    }
    public void addToUserQueue(UUID userId, Track track) {
        PartyUser user = getPartyUser(userId);
        if (user != null) {
            synchronized (this) {
                user.addTrack(track);
                queue.partyQueueChanged();
                if (partyPlayer != null) {
                    partyPlayer.notifyNewTrackAdded();
                }
            }
        }
    }
    public void removeFromUserQueue(UUID userId, UUID queueItemId) {
        PartyUser user = getPartyUser(userId);
        if (user != null) {
            user.removeTrack(queueItemId);
            queue.partyQueueChanged();
            messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
        }
    }

    public Map<UUID, AddedTrack> getPartyQueue() {
        return queue.getQueue();
    }
    public AddedTrack getCurrentlyPlaying() {
        if (partyPlayer == null) return null;
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

    // method for PartyPlayer
    public AddedTrack pollFallbackTrack(String token) {
        log.info("Polling fallback track with total tracks {}", totalFallbackTracks);

        if (totalFallbackTracks == -1) return null;

        CompletableFuture<Track> fallbackTrackFuture = cachedFallbackTrackFuture.getAndSet(null);
        triggerFallbackTrackReplenishment(token);

        try {
            Track fallbackTrack = fallbackTrackFuture.join();
            if (fallbackTrack == null) return null;
            return new AddedTrack(fallbackTrack, ownerProfile);
        } catch (Exception e) {
            log.error("Error while grabbing fallback track from future", e);
            return null;
        }
    }

    // methods for host
    public List<UserWithId> getUsersWithId() {
        return userMap.values().stream()
                .map(user -> new UserWithId(user.getId(), user.getProfile()))
                .filter(userWithId -> userWithId.profile() != null && !Objects.equals(userWithId.profile().spotifyId(), partyId)) // remove the host from the list
                .toList();
    }

    private void incrementFallbackIndex() {
        fallbackIndex++;
        if (fallbackIndex >= totalFallbackTracks) {
            int lastIndex = fallbackTrackIds.get(totalFallbackTracks - 1);
            fallbackIndex = 0;
            log.info("Shuffling fallback track IDs");
            Collections.shuffle(fallbackTrackIds);
            if (totalFallbackTracks > 1 && fallbackTrackIds.getFirst() == lastIndex) {
                int element = fallbackTrackIds.removeFirst();
                fallbackTrackIds.add(element);
            }
        }
    }
    private void triggerFallbackTrackReplenishment(String token) {
        if (totalFallbackTracks == -1) return;
        int nextTrackIndex = fallbackTrackIds.get(fallbackIndex);
        incrementFallbackIndex();

        CompletableFuture<Track> nextFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Track fallbackTrack = spotifyProxyService.getPlaylistTrack(token, partySettings.fallbackPlaylistId(), nextTrackIndex);
                if (fallbackTrack == null) {
                    log.warn("Failed to fetch fallback track at index {} from playlist {}, skipping to next", nextTrackIndex, partySettings.fallbackPlaylistId());
                }
                return fallbackTrack;
            } catch (Exception e) {
                log.error("Error while replenishing fallback track", e);
                return null;
            }
        });

        cachedFallbackTrackFuture.set(nextFuture);
    }
    private PartyUser getPartyUser(UUID userId) {
        return userMap.get(userId);
    }
    private Map<UUID, Track> deleteDuplicateUser(UserProfile profile, UUID validUserId) {
        if (profile.spotifyAuthorized()) {
            for (PartyUser user : userMap.values()) {
                if (user.getId() == validUserId) continue;
                UserProfile userProfile = user.getProfile();
                if (userProfile.spotifyAuthorized() && Objects.equals(userProfile.spotifyId(), profile.spotifyId())) {
                    Map<UUID, Track> oldQueue = user.getQueue();
                    removeUser(user.getId());
                    log.info("Removed duplicate spotify user");
                    return oldQueue;
                }
            }
        }
        return null;
    }
}
