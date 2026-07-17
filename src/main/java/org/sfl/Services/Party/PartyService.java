package org.sfl.Services.Party;

import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.Music.Track;
import org.sfl.DTOs.Party.PartyQueueInfo;
import org.sfl.DTOs.Party.PartySettings;
import org.sfl.DTOs.Party.SimpleResponse;
import org.sfl.DTOs.User.SafeUserProfile;
import org.sfl.DTOs.User.UserData;
import org.sfl.DTOs.User.UserProfile;
import org.sfl.DTOs.User.UserWithId;
import org.sfl.Enums.MessageType;
import org.sfl.Exceptions.PartyNotFoundException;
import org.sfl.Objects.Party.PartyPlayer;
import org.sfl.Objects.Party.PartySession;
import org.sfl.Services.Messages.MessagingService;
import org.sfl.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.Services.Spotify.SpotifyPlayerService;
import org.sfl.Services.Spotify.SpotifyProxyService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PartyService {

    private final SpotifyProxyService spotifyProxyService;
    private final MessagingService messagingService;
    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;

    // (spotifyId - party) map
    private final Map<String, PartySession> partySessionMap = new ConcurrentHashMap<>();

    public PartyService(SpotifyProxyService spotifyProxyService, MessagingService messagingService, SpotifyAuthorizedClientService spotifyAuthorizedClientService) {
        this.spotifyProxyService = spotifyProxyService;
        this.messagingService = messagingService;
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
    }


    public void createParty(UserData user, PartySettings partySettings) {
        String spotifyUserId = user.getSpotifyId();

        // get playlist total tracks
        int totalTracks = -1;
        if (partySettings.fallbackPlaylistId() != null) {
            OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user);
            totalTracks = spotifyProxyService.getTotalNumberOfTracksInPlaylist(authorizedClient, partySettings.fallbackPlaylistId());
        }

        log.info("Creating party for user with spotify id {}", spotifyUserId);
        log.info("Settings used for creation this party: voteToSkip {}, percentVoting {}, voteThreshold {}, instantSelfSkip {}, fallbackPlaylistId {}", partySettings.voteToSkip(), partySettings.percentVoting(), partySettings.voteThreshold(), partySettings.instantSelfSkip(), partySettings.fallbackPlaylistId());

        String userToken = spotifyAuthorizedClientService.getAuthorizedClient(user).getAccessToken().getTokenValue();

        //create new party for user
        PartySession party = new PartySession(user, userToken, partySettings, totalTracks, messagingService, spotifyProxyService);
        partySessionMap.put(spotifyUserId, party);
        user.setPartyId(spotifyUserId);
    }

    public SimpleResponse joinParty(String partyId, UserData user, boolean asParticipant, boolean asPlayer, boolean asHost) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        boolean isOwner = Objects.equals(party.getPartyId(), user.getSpotifyId());

        user.clearRoles();

        if (asPlayer) {
            if (isOwner)
                user.setPlayer(true);
            else
                return new SimpleResponse(false, "Only party owner can join as player");
        }
        if (asHost) {
            if (isOwner)
                user.setHost(true);
            else
                return new SimpleResponse(false, "Only party owner can join as host");
        }

        if (asParticipant) {
            if (party.isUserInParty(user.getUserId())) {
                log.warn("User {} is already in party with id {}, skipping join", user.getUserId(), partyId);
                user.setUser(true); // error prevention
            } else {
                user.setUser(true);
                UserProfile profile = new UserProfile(user.getDisplayName(), user.isSpotifyAuthenticated(), user.getSpotifyId(), user.getImageUrl(), user.getSmallImageUrl());
                party.addUser(profile, user);
                messagingService.sendUpdate(partyId, MessageType.PARTY_USERS_CHANGED);
            }
        }

        // mark that this session is connected with this party
        user.setPartyId(partyId);
        return new SimpleResponse(true, "Joined party successfully");
    }

    // returns true if user was a participant of this party and was removed
    public boolean removeUserFromParty(UserData user) {
        validatePartyId(user.getPartyId());
        PartySession party = Optional.ofNullable(partySessionMap.get(user.getPartyId()))
                .orElseThrow(() -> new PartyNotFoundException(user.getPartyId()));
        if (user.isUser()) // is in userMap and even if he is also a player he will be cleared
            return party.removeUser(user.getUserId());
        if (user.isPlayer()) // remove if user is player or user is participant
            return party.removePlayerSession(user);
        if (user.isHost()) // clear session data of that guy
        {
            user.clearRoles();
            user.setPartyId(null);
            messagingService.sendPrivateUpdate(user.getUserId(), MessageType.REFRESH_STATUS);
        }
        return false;
    }

    public void updateUserProfile(String partyId, UserData user) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        UserProfile profile = new UserProfile(user.getDisplayName(), user.isSpotifyAuthenticated(), user.getSpotifyId(), user.getImageUrl(), user.getSmallImageUrl());
        party.updateUser(user.getUserId(), profile, user);
    }

    public void initializePartyPlayer(UserData user, String deviceId, SpotifyAuthorizedClientService spotifyAuthorizedClientService, SpotifyPlayerService spotifyPlayerService) {
        validatePartyId(user.getPartyId());
        PartySession party = Optional.ofNullable(partySessionMap.get(user.getPartyId()))
                .orElseThrow(() -> new PartyNotFoundException(user.getPartyId()));

        PartyPlayer player = new PartyPlayer(
                deviceId,
                user,
                spotifyAuthorizedClientService,
                spotifyPlayerService,
                messagingService,
                party
        );

        log.info("Initializing party player for user {} in party {} with deviceId: {}", user.getUserId(), user.getPartyId(), deviceId);
        party.initializePlayer(player);
    }

    public void clearPlayer(UserData user) {
        validatePartyId(user.getPartyId());
        PartySession party = Optional.ofNullable(partySessionMap.get(user.getPartyId()))
                .orElseThrow(() -> new PartyNotFoundException(user.getPartyId()));
        party.clearPlayer();
    }

    public boolean playNextTrack(String partyId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.playNext();
    }

    public void addToUserQueue(String partyId, UUID userId, String trackId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        if (trackId == null || trackId.isEmpty()) {
            log.warn("Invalid track id provided by user {}, cannot add to queue", userId);
            return;
        }

        Track track = spotifyProxyService.getTrack(trackId);

        if (track == null) {
            log.warn("Track with id {} not found, cannot add to queue", trackId);
            return;
        }

        log.info("Adding track {} to user {} queue in party {}", track.getName(), userId, partyId);

        party.addToUserQueue(userId, track);
        messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
    }

    public Map<UUID, Track> getUserQueue(String partyId, UUID userId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.getUserQueue(userId);
    }

    public void removeFromUserQueue(String partyId, UUID userId, UUID queueItemId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        log.info("removing track with queueItemId: {} on user {} queue in party {}", queueItemId, userId, partyId);

        party.removeFromUserQueue(userId, queueItemId);
    }

    public PartyQueueInfo getPartyQueueInfo(String partyId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return new PartyQueueInfo(party.getPartyQueue(), party.getCurrentlyPlaying());
    }

    public List<SafeUserProfile> getPartyUsers(String partyId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.getPartyUsers()
                .stream()
                .map(user -> new SafeUserProfile(
                        user.displayName(),
                        user.spotifyAuthorized(),
                        user.profileImageUrl(),
                        user.smallProfileImageUrl()))
                .toList();
    }

    public int voteForSkip(String partyId, UUID userId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.voteForSkip(userId);
    }

    public int cancelUserSkipVote(String partyId, UUID userId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
            .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.cancelUserSkipVote(userId);
    }

    public List<UserWithId> getHostUsers(String partyId) {
        validatePartyId(partyId);
        PartySession party = partySessionMap.get(partyId);
        return party.getUsersWithId();
    }

    private void validatePartyId(String partyId) {
        if (partyId == null) {
            log.error("Tried to invoke method in partyService with partyId == null");
            throw new IllegalStateException("Party ID cannot be null");
        }
    }
}