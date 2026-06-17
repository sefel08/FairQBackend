package org.sfl.spotifybackendnew.Controllers;

import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.PartyQueueInfo;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.Party.SimpleResponse;
import org.sfl.spotifybackendnew.DTOs.User.SafeUserProfile;
import org.sfl.spotifybackendnew.Exceptions.PartyNotFoundException;
import org.sfl.spotifybackendnew.Services.Party.PartyService;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        String partyId = user.getPartyId();
        boolean inParty = partyId != null;

        if (!inParty) {
            return Map.of("inParty", false);
        }

        return Map.of(
                "inParty", true,
                "partyId", partyId
        );
    }

    public record JoinPartyRequest(boolean asParticipant, boolean asPlayer, boolean asHost) {}

    @PostMapping
    public String createParty(@AuthenticationPrincipal UserData user, @RequestBody PartySettings partySettings) {
        partyService.createParty(user.getSpotifyId(), partySettings);
        return user.getSpotifyId();
    }
    @PostMapping("/join")
    public SimpleResponse joinParty(@AuthenticationPrincipal UserData user, @RequestParam String partyId, @RequestBody JoinPartyRequest joinPartyRequest) {
        if (partyId == null)
            return new SimpleResponse(false, "Party ID is required");
        return partyService.joinParty(partyId, user, joinPartyRequest.asParticipant, joinPartyRequest.asPlayer, joinPartyRequest.asHost);
    }
    @PostMapping("/joinOwn")
    public SimpleResponse joinOwnParty(@AuthenticationPrincipal UserData user, @RequestBody JoinPartyRequest joinPartyRequest) {
        if (user.getSpotifyId() == null)
            return new SimpleResponse(false, "You must be logged in via Spotify");
        return partyService.joinParty(user.getSpotifyId(), user, joinPartyRequest.asParticipant, joinPartyRequest.asPlayer, joinPartyRequest.asHost);
    }

    @PostMapping("/leave")
    public SimpleResponse leaveParty(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null)
            return new SimpleResponse(false, "You are not in a party");

        try {
            log.info("User {} is leaving party {}", user.getUserId(), user.getPartyId());
            partyService.removeUserFromParty(user.getPartyId(), user.getUserId());
            user.setPartyId(null);
            return new SimpleResponse(true, "Left the party successfully");
        } catch (PartyNotFoundException e) {
            log.error("Party not found for user {} when trying to leave", user.getUserId());
            return new SimpleResponse(false, "Party not found");
        }
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
    public PartyQueueInfo getPartyQueue(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null) {
            return new PartyQueueInfo(List.of(), null);
        }
        return partyService.getPartyQueueInfo(user.getPartyId());
    }

    @GetMapping("/users")
    public List<SafeUserProfile> getPartyUsers(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null) {
            return List.of();
        }
        return partyService.getPartyUsers(user.getPartyId());
    }

    @PostMapping("/skip")
    public int voteForSkip(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null)
            return 0;

        try {
            return partyService.voteForSkip(user.getPartyId(), user.getUserId());
        } catch (PartyNotFoundException e) {
            log.error("Party not found for user {} when voting for skip", user.getUserId());
            return 0;
        }
    }
    @DeleteMapping("/skip")
    public int cancelSkipVote(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null)
            return 0;

        try {
            return partyService.cancelUserSkipVote(user.getPartyId(), user.getUserId());
        } catch (PartyNotFoundException e) {
            log.error("Party not found for user {} when canceling his skip", user.getUserId());
            return 0;
        }
    }
}