package org.sfl.spotifybackendnew.Services.Party;

import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
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
import org.springframework.security.core.Authentication;
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
        log.info("Settings used for creation this party: voteToSkip {}, percentVoting {}, voteThreshold {}", partySettings.voteToSkip(), partySettings.percentVoting(), partySettings.voteThreshold());

        //create new party for user
        PartySession party = new PartySession(spotifyUserId, partySettings);
        partySessionMap.put(spotifyUserId, party);
    }

    public void joinParty(String partyId, UserData user) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        UserProfile profile = new UserProfile(user.getDisplayName(), user.isSpotifyAuthenticated(), user.getSpotifyId(), user.getImageUrl(), user.getSmallImageUrl());
        party.addUser(user.getUserId(), profile);
        user.setPartyId(partyId);
    }

    public void removeUserFromParty(String partyId, UUID userId) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        party.removeUser(userId);
    }

    public void updateUserProfile(String partyId, UserData user) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        UserProfile profile = new UserProfile(user.getDisplayName(), user.isSpotifyAuthenticated(), user.getSpotifyId(), user.getImageUrl(), user.getSmallImageUrl());
        party.updateUser(user.getUserId(), profile);
    }

    public void initializePartyPlayer(UserData user, Authentication authentication, String deviceId, SpotifyAuthorizedClientService spotifyAuthorizedClientService, SpotifyPlayerService spotifyPlayerService) {
        PartySession party = Optional.ofNullable(partySessionMap.get(user.getPartyId()))
                .orElseThrow(() -> new PartyNotFoundException(user.getPartyId()));

        PartyPlayer player = new PartyPlayer(
                deviceId,
                user,
                authentication,
                spotifyAuthorizedClientService,
                spotifyPlayerService,
                messagingService,
                party
        );

        log.info("Initializing party player for user {} in party {}", user.getUserId(), user.getPartyId());
        party.initializePlayer(player);
    }

    public void clearPlayer(String partyId) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        log.info("Clearing player for party {}", partyId);
        party.clearPlayer();
    }

    public boolean playNextTrack(String partyId) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.playNext();
    }

    public void addToUserQueue(String partyId, UUID userId, String trackId) {
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
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.getUserQueue(userId);
    }

    public void removeFromUserQueue(String partyId, UUID userId, int index) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        log.info("removing track #{} on user {} queue in party {}", index, userId, partyId);

        party.removeFromUserQueue(userId, index);
    }

    public List<AddedTrack> getPartyQueue(String partyId) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.getPartyQueue();
    }

    public List<UserProfile> getPartyUsers(String partyId) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.getPartyUsers();
    }

    public boolean voteForSkip(String partyId, UUID userId) {
        PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.voteForSkip(userId);
    }

    public boolean cancelUserSkipVote(String partyId, UUID userId) {
    PartySession party = Optional.ofNullable(partySessionMap.get(partyId))
            .orElseThrow(() -> new PartyNotFoundException(partyId));
        return party.cancelUserSkipVote(userId);
    }
}