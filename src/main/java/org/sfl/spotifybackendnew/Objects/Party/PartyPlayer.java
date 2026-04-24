package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyPlayerService;
import org.springframework.security.core.Authentication;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PartyPlayer {
    private final String deviceId;
    private final UserData playerUser;
    private final Authentication playerAuthentication;

    @Setter
    private SmartQueue partyQueue;
    private final AtomicBoolean waitsForNewTrack = new AtomicBoolean(false);

    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;
    private final SpotifyPlayerService spotifyPlayerService;

    public PartyPlayer(
            String deviceId,
            UserData playerUser,
            Authentication playerAuthentication,
            SpotifyAuthorizedClientService spotifyAuthorizedClientService,
            SpotifyPlayerService spotifyPlayerService
    ) {
        this.deviceId = deviceId;
        this.playerUser = playerUser;
        this.playerAuthentication = playerAuthentication;
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
        this.spotifyPlayerService = spotifyPlayerService;
    }

    public synchronized boolean playNextTrack() {
        Track nextTrack = partyQueue.pollTrack();
        // if party queue is empty
        if (nextTrack == null) {
            waitsForNewTrack.set(true);
            log.info("Party player in party {} is waiting for new track", playerUser.getPartyId());
            return false;
        }
        spotifyPlayerService.playTrack(
                spotifyAuthorizedClientService.getAuthorizedClient(playerUser, playerAuthentication),
                nextTrack.getUri(),
                deviceId
        );
        return true;
    }
    public synchronized void notifyNewTrackAdded() {
        if (waitsForNewTrack.compareAndSet(true, false)) {
            playNextTrack();
        }
    }
}