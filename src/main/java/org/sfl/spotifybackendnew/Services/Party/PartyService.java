package org.sfl.spotifybackendnew.Services.Party;

import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.PartyQueueInfo;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.Party.SimpleResponse;
import org.sfl.spotifybackendnew.DTOs.User.SafeUserProfile;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;
import org.sfl.spotifybackendnew.Enums.MessageType;
import org.sfl.spotifybackendnew.Exceptions.PartyNotFoundException;
import org.sfl.spotifybackendnew.Objects.Party.PartyPlayer;
import org.sfl.spotifybackendnew.Objects.Party.PartySession;
import org.sfl.spotifybackendnew.Services.Messages.MessagingService;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyPlayerService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyProxyService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PartyService {

    private final SpotifyProxyService spotifyProxyService;
    private final MessagingService messagingService;

    // (spotifyId - party) map
    private final Map<String, PartySession> partySessionMap = new ConcurrentHashMap<>();

    public PartyService(SpotifyProxyService spotifyProxyService, MessagingService messagingService) {
        this.spotifyProxyService = spotifyProxyService;
        this.messagingService = messagingService;
    }


    public void createParty(String spotifyUserId, PartySettings partySettings) {
        PartySession userPartySession = partySessionMap.get(spotifyUserId);
        if (userPartySession != null) return;

        log.info("Creating party for user with spotify id {}", spotifyUserId);
        log.info("Settings used for creation this party: voteToSkip {}, percentVoting {}, voteThreshold {}, instantSelfSkip {}", partySettings.voteToSkip(), partySettings.percentVoting(), partySettings.voteThreshold(), partySettings.instantSelfSkip());

        //create new party for user
        PartySession party = new PartySession(spotifyUserId, partySettings, messagingService);
        partySessionMap.put(spotifyUserId, party);
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

    public void removeUserFromParty(String partyId, UUID userId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        if (party.isUserInParty(userId)) {
            party.removeUser(userId);
            messagingService.sendUpdate(partyId, MessageType.PARTY_USERS_CHANGED);
        }
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

    public void clearPlayer(UserData user, String partyId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        log.info("Clearing player for party {}", partyId);
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

    public List<Track> getUserQueue(String partyId, UUID userId) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.getUserQueue(userId);
    }

    public void removeFromUserQueue(String partyId, UUID userId, int index) {
        validatePartyId(partyId);
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        log.info("removing track #{} on user {} queue in party {}", index, userId, partyId);

        party.removeFromUserQueue(userId, index);
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


    private void validatePartyId(String partyId) {
        if (partyId == null) {
            log.error("Tried to invoke method in partyService with partyId == null");
            throw new IllegalStateException("Party ID cannot be null");
        }
    }
}