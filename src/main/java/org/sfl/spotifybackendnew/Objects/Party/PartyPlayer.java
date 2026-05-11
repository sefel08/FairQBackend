package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.Message;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Enums.MessageType;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;
import org.sfl.spotifybackendnew.Services.Messages.MessagingService;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyPlayerService;
import org.springframework.security.core.Authentication;

import java.util.HashSet;
import java.util.List;
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
    private final MessagingService messagingService;
    private final PartySession partySession;
    private final String partyId;

    public PartyPlayer(
            String deviceId,
            UserData playerUser,
            Authentication playerAuthentication,
            SpotifyAuthorizedClientService spotifyAuthorizedClientService,
            SpotifyPlayerService spotifyPlayerService,
            MessagingService messagingService,
            PartySession partySession
    ) {
        this.deviceId = deviceId;
        this.playerUser = playerUser;
        this.playerAuthentication = playerAuthentication;
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
        this.spotifyPlayerService = spotifyPlayerService;
        this.messagingService = messagingService;
        this.partySession = partySession;
        this.partyId = partySession.getPartyId();
    }

    public synchronized boolean playNextTrack(boolean forceSkip) {
        Track nextTrack = partyQueue.peekTrack();

        // if party queue is empty
        if (nextTrack == null) {
            waitsForNewTrack.set(true);
            log.info("Party player in party {} is waiting for new track", partyId);

            if (forceSkip) {
                // play 1 second of silence
                nextTrack = new Track(
                        "4jaXxB0DJ6X4PdjMK8XVfu",
                        "",
                        List.of(),
                        "",
                        0,
                        "https://open.spotify.com/track/4jaXxB0DJ6X4PdjMK8XVfu",
                        "spotify:track:4jaXxB0DJ6X4PdjMK8XVfu"
                );
            } else {
                return false;
            }
        }

        boolean success = spotifyPlayerService.playTrack(
                spotifyAuthorizedClientService.getAuthorizedClient(playerUser, playerAuthentication),
                nextTrack.getUri(),
                deviceId
        );

        if (success) {
            partyQueue.pollTrack();
            skipVotes.clear();
            waitsForNewTrack.set(false);
            messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
            messagingService.sendUpdate(partyId, new Message(MessageType.SKIP_VOTES_CHANGED, skipVotes.size()));
            return true;
        } else {
            log.warn("Failed to play track for party {}. Player device might be offline.", partyId);
            return false;
        }
    }
    public synchronized void notifyNewTrackAdded() {
        if (waitsForNewTrack.compareAndSet(true, false)) {
            playNextTrack(false);
        }
    }

    public synchronized int voteForSkip(UUID userId) {
        if (waitsForNewTrack.get()) return 0; // cannot skip if waiting for new track

        if (skipVotes.add(userId)) {
            log.info("User {} voted to skip the current track in party {}", userId, partyId);
        } else {
            log.info("User {} tried to skip but already has skipped in party {}", userId, partyId);
            return 0;
        }

        boolean skipped = handleSkipping();
        messagingService.sendUpdate(partyId, new Message(MessageType.SKIP_VOTES_CHANGED, skipVotes.size()));
        return skipped ? -1 : 1;
    }
    public synchronized int cancelUserSkipVote(UUID userId) {
        if (skipVotes.remove(userId)) {
            log.info("User {} removed his vote to skip the current track in party {}", userId, partyId);
        } else {
            log.info("User {} wanted to remove his vote but has not voted for skip {}", userId, partyId);
            return 0;
        }
        messagingService.sendUpdate(partyId, new Message(MessageType.SKIP_VOTES_CHANGED, skipVotes.size()));
        return 1;
    }

    private boolean handleSkipping() {
        PartySettings settings = partySession.getPartySettings();
        if (!settings.voteToSkip()) return false;

        int voteCount = skipVotes.size();
        boolean shouldSkip = isSkip(settings, voteCount);

        if (shouldSkip) {
            playNextTrack(true);
            skipVotes.clear();
            log.info("Track skipped in party {} with {} skip votes", partyId, voteCount);
            return true;
        }

        return false;
    }
    private boolean isSkip(PartySettings settings, int voteCount) {
        boolean shouldSkip;

        if (settings.percentVoting()) {
            int totalUsers = partySession.getTotalUsers();
            int percentVotesThreshold = (int) (totalUsers * settings.voteThreshold());
            shouldSkip = settings.moreThanThreshold() ? voteCount > percentVotesThreshold : voteCount >= percentVotesThreshold;
        } else { // if not percent voting, there must be moreThanThreshold votes than threshold to skip
            shouldSkip = settings.moreThanThreshold() ? voteCount > settings.voteThreshold() : voteCount >= settings.voteThreshold();
        }
        return shouldSkip;
    }
}