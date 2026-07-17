package org.sfl.Controllers;

import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.Music.Track;
import org.sfl.DTOs.Party.PartyQueueInfo;
import org.sfl.DTOs.Party.PartySettings;
import org.sfl.DTOs.Party.SimpleResponse;
import org.sfl.DTOs.User.SafeUserProfile;
import org.sfl.DTOs.User.UserWithId;
import org.sfl.Exceptions.PartyNotFoundException;
import org.sfl.Services.Party.PartyService;
import org.sfl.DTOs.User.UserData;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
        partyService.createParty(user, partySettings);
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
            partyService.removeUserFromParty(user);
            return new SimpleResponse(true, "Left the party successfully");
        } catch (PartyNotFoundException e) {
            log.error("Party not found for user {} when trying to leave", user.getUserId());
            return new SimpleResponse(false, "Party not found");
        }
    }

    public record AddTrackRequest(String trackId) {}
    public record DeleteTrackRequest(UUID queueItemId) {}

    @GetMapping("/queue")
    public Map<UUID, Track> getQueue(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null) {
            return Map.of();
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
        partyService.removeFromUserQueue(user.getPartyId(), user.getUserId(), deleteTrackRequest.queueItemId);
    }

    @GetMapping("/partyQueue")
    public PartyQueueInfo getPartyQueue(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null) {
            return new PartyQueueInfo(Map.of(), null);
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

    // host mappings

    public record RemoveUserRequest(UUID userId) {}

    @GetMapping("/host/users")
    public List<UserWithId> getHostUsers(@AuthenticationPrincipal UserData user) {
        if (user.getPartyId() == null || !Objects.equals(user.getPartyId(), user.getSpotifyId())) {
            return List.of();
        }
        return partyService.getHostUsers(user.getPartyId());
    }
    @DeleteMapping("/host/users")
    public SimpleResponse removeUser(@AuthenticationPrincipal UserData user, @RequestBody RemoveUserRequest request) {
        if (user.getPartyId() == null || !Objects.equals(user.getPartyId(), user.getSpotifyId())) {
            return new SimpleResponse(false, "You are not the owner of the party");
        }
        if (request.userId == null) {
            return new SimpleResponse(false, "You must send userId to remove");
        }
        boolean removed = partyService.removeUserFromParty(user);
        if (removed) {
            return new SimpleResponse(true, "User removed successfully");
        } else {
            return new SimpleResponse(false, "User is not in the party");
        }
    }
}