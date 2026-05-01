package org.sfl.spotifybackendnew.Controllers;

import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;
import org.sfl.spotifybackendnew.Exceptions.PartyNotFoundException;
import org.sfl.spotifybackendnew.Services.Party.PartyService;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/party")
public class PartyController {

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    @GetMapping("/status")
    public Map<String, Object> checkPartyStatus(@AuthenticationPrincipal UserData user) {
        boolean inParty = user.getPartyId() != null;
        String partyId = user.getPartyId();

        if (inParty) {
            return Map.of(
                    "inParty", inParty,
                    "partyId", partyId,
                    "isHost", Objects.equals(user.getSpotifyId(), partyId)
            );
        }

        return Map.of("inParty", inParty);
    }

    @PostMapping
    public String createParty(@AuthenticationPrincipal UserData user, @RequestBody PartySettings partySettings) {
        partyService.createParty(user.getSpotifyId(), partySettings);
        return user.getSpotifyId();
    }
    @PostMapping("/join")
    public void joinParty(@AuthenticationPrincipal UserData user, @RequestParam String partyId) {
        partyService.joinParty(partyId, user);
    }

    public record AddTrackRequest(String trackId) {}
    public record DeleteTrackRequest(int index) {}

    @GetMapping("/queue")
    public List<Track> getQueue(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null) {
            return List.of();
        }
        return partyService.getUserQueue(user.getPartyId(), user.getUserId());
    }
    @PostMapping("/queue")
    public void addToQueue(@AuthenticationPrincipal UserData user, @RequestBody AddTrackRequest addTrackRequest) {
        if (user.getPartyId() == null) {
            return;
        }
        partyService.addToUserQueue(user.getPartyId(), user.getUserId(), addTrackRequest.trackId);
    }
    @DeleteMapping("/queue")
    public void removeFromQueue(@AuthenticationPrincipal UserData user, @RequestBody DeleteTrackRequest deleteTrackRequest) {
        if (user.getPartyId() == null) {
            return;
        }
        partyService.removeFromUserQueue(user.getPartyId(), user.getUserId(), deleteTrackRequest.index);
    }

    @GetMapping("/partyQueue")
    public List<AddedTrack> getPartyQueue(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null) {
            return List.of();
        }
        return partyService.getPartyQueue(user.getPartyId());
    }

    @GetMapping("/users")
    public List<UserProfile> getPartyUsers(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null) {
            return List.of();
        }
        return partyService.getPartyUsers(user.getPartyId());
    }

    @PostMapping("/skip")
    public boolean voteForSkip(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null)
            return false;

        try {
            return partyService.voteForSkip(user.getPartyId(), user.getUserId());
        } catch (PartyNotFoundException e) {
            log.error("Party not found for user {} when voting for skip", user.getUserId());
            return false;
        }
    }
}