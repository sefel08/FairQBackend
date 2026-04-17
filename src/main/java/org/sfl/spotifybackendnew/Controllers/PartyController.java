package org.sfl.spotifybackendnew.Controllers;

import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.Services.Party.PartyService;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/party")
public class PartyController {

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    // only for authenticated via spotify users, so UserData always contain spotifyId
    @PostMapping("/create")
    public void createParty(@AuthenticationPrincipal UserData user) {
        partyService.createParty(user.getSpotifyId());
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
        return partyService.getQueue(user.getPartyId(), user.getUserId());
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
}