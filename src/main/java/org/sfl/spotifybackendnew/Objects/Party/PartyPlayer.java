package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyPlayerService;
import org.springframework.security.core.Authentication;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PartyPlayer {
    // player device data
    private final String deviceId;
    private final UserData playerUser;
    private final Authentication playerAuthentication;

    @Setter
    private SmartQueue partyQueue;
    private final AtomicBoolean waitsForNewTrack = new AtomicBoolean(false);
    private final Set<UUID> skipVotes = new HashSet<>();

    // services and references
    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;
    private final SpotifyPlayerService spotifyPlayerService;
    private final PartySession partySession;

    public PartyPlayer(
            String deviceId,
            UserData playerUser,
            Authentication playerAuthentication,
            SpotifyAuthorizedClientService spotifyAuthorizedClientService,
            SpotifyPlayerService spotifyPlayerService,
            PartySession partySession
    ) {
        this.deviceId = deviceId;
        this.playerUser = playerUser;
        this.playerAuthentication = playerAuthentication;
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
        this.spotifyPlayerService = spotifyPlayerService;
        this.partySession = partySession;
    }

    public synchronized boolean playNextTrack() {
        Track nextTrack = partyQueue.peekTrack();

        // if party queue is empty
        if (nextTrack == null) {
            waitsForNewTrack.set(true);
            log.info("Party player in party {} is waiting for new track", playerUser.getPartyId());
            return false;
        }

        boolean success = spotifyPlayerService.playTrack(
                spotifyAuthorizedClientService.getAuthorizedClient(playerUser, playerAuthentication),
                nextTrack.getUri(),
                deviceId
        );

        if (success) {
            partyQueue.pollTrack();
            waitsForNewTrack.set(false);
            return true;
        } else {
            log.warn("Failed to play track for party {}. Player device might be offline.", playerUser.getPartyId());
            return false;
        }
    }
    public synchronized void notifyNewTrackAdded() {
        if (waitsForNewTrack.compareAndSet(true, false)) {
            playNextTrack();
        }
    }

    public synchronized boolean voteForSkip(UUID userId) {
        if (skipVotes.add(userId)) {
            log.info("User {} voted to skip the current track in party {}", userId, playerUser.getPartyId());
        } else {
            log.info("User {} has already voted to skip the current track in party {}", userId, playerUser.getPartyId());
        }
        handleSkipping();
        return true;
    }


    private void handleSkipping() {
        int totalUsers = partySession.getTotalUsers();
        int voteCount = skipVotes.size();
        PartySettings settings = partySession.getPartySettings();

        if (!settings.voteToSkip()) return;

        // if percent voting is enabled, calculate the threshold based on total users, otherwise use the fixed threshold
        boolean shouldSkip = voteCount > ((settings.percentVoting()) ? totalUsers * settings.voteThreshold() : settings.voteThreshold());

        if (shouldSkip) {
            playNextTrack();
            skipVotes.clear();
            log.info("Track skipped in party {} with {} skip votes out of {} users", playerUser.getPartyId(), voteCount, totalUsers);
        }
    }
}