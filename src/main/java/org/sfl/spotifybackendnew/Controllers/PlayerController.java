package org.sfl.spotifybackendnew.Controllers;

import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Services.Party.PartyService;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyPlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/player")
public class PlayerController {

    private static final Logger log = LoggerFactory.getLogger(PlayerController.class);
    private final PartyService partyService;
    private final SpotifyPlayerService spotifyPlayerService;
    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;

    public PlayerController(PartyService partyService, SpotifyPlayerService spotifyPlayerService, SpotifyAuthorizedClientService spotifyAuthorizedClientService) {
        this.partyService = partyService;
        this.spotifyPlayerService = spotifyPlayerService;
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
    }


    public record SetupRequest(String deviceId) {}

    @PostMapping("/setup")
    public void setupPlayer(@AuthenticationPrincipal UserData user, Authentication authentication, @RequestBody SetupRequest setupRequest) {
        try {
            if (!user.isHasHostPermissions() || !user.isPremium()) return;
            OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user, authentication);
            partyService.initializePartyPlayer(user, authentication, setupRequest.deviceId, spotifyAuthorizedClientService, spotifyPlayerService);
//            spotifyPlayerService.setupPlayer(authorizedClient, setupRequest.deviceId);
//            Thread.sleep(1500); // wait for player to initialize
        } catch (Exception e) {
            log.error("Error setting up player for user {}: {}", user.getUserId(), e.getMessage());
        }
    }

    record PlayNextResponse(boolean played, String message) {}
    @PostMapping("/playNext")
    public ResponseEntity<?> playNextTrack(@AuthenticationPrincipal UserData user) {
        if (!user.isHasHostPermissions() || !user.isPremium()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new PlayNextResponse(false, "Missing Host permissions or Premium account"));
        }

        if (!Objects.equals(user.getPartyId(), user.getSpotifyId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new PlayNextResponse(false, "You are not the owner of this party"));
        }

        boolean played = partyService.playNextTrack(user.getPartyId());

        if (!played) {
            return ResponseEntity.ok(new PlayNextResponse(false, "Queue is empty or player is out"));
        }

        return ResponseEntity.ok(new PlayNextResponse(true, "Successfully skipped to next track"));
    }
}