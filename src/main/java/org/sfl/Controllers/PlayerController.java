package org.sfl.Controllers;

import org.sfl.DTOs.User.UserData;
import org.sfl.Exceptions.PartyNotFoundException;
import org.sfl.Services.Party.PartyService;
import org.sfl.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.Services.Spotify.SpotifyPlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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


    public record DeviceIdRequest(String deviceId) {}

    @PostMapping("/setup")
    public ResponseEntity<?> setupPlayer(@AuthenticationPrincipal UserData user, @RequestBody DeviceIdRequest deviceIdRequest) {
        if (user.getPartyId() == null || !Objects.equals(user.getPartyId(), user.getSpotifyId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only party owner can setup player");
        }
        if (!user.isHasSpotifyPlayerPermissions() || !user.isPremium()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Missing player permissions or Premium account required");
        }
        partyService.initializePartyPlayer(user, deviceIdRequest.deviceId, spotifyAuthorizedClientService, spotifyPlayerService);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanupPlayer(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null || !Objects.equals(user.getPartyId(), user.getSpotifyId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only party owner can cleanup player");
        }

        try {
            partyService.clearPlayer(user, user.getPartyId());
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (PartyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Party connected with this device id does not exist");
        }
    }

    record PlayNextResponse(boolean played, String message) {}
    @PostMapping("/playNext")
    public ResponseEntity<?> playNextTrack(@AuthenticationPrincipal UserData user) {
        if (!Objects.equals(user.getPartyId(), user.getSpotifyId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new PlayNextResponse(false, "You are not the owner of this party"));
        }

        if (!user.isHasSpotifyPlayerPermissions() || !user.isPremium()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new PlayNextResponse(false, "Missing Host permissions or Premium account"));
        }

        if (user.getPartyId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You must be in a party to play next track");
        }

        boolean played = partyService.playNextTrack(user.getPartyId());

        if (!played) {
            return ResponseEntity.ok(new PlayNextResponse(false, "Queue is empty or player is out"));
        }

        return ResponseEntity.ok(new PlayNextResponse(true, "Successfully skipped to next track"));
    }
}