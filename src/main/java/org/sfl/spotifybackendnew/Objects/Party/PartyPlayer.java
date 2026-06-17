package org.sfl.spotifybackendnew.Objects.Party;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Party.Message;
import org.sfl.spotifybackendnew.DTOs.Party.PartySettings;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Enums.MessageType;
import org.sfl.spotifybackendnew.Objects.SmartQueue.SmartQueue;
import org.sfl.spotifybackendnew.Services.Messages.MessagingService;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyPlayerService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PartyPlayer {
    private final String partyId;

    // player device data
    private final String deviceId;
    private final UserData playerUserSession;
    private String userToken; // made to detect token refresh

    @Setter
    private SmartQueue partyQueue;
    @Getter
    private AddedTrack currentlyPlaying;
    private final AtomicBoolean waitsForNewTrack = new AtomicBoolean(false);
    private final Set<UUID> skipVotes = new HashSet<>();

    // services and references
    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;
    private final SpotifyPlayerService spotifyPlayerService;
    private final MessagingService messagingService;
    private final PartySession partySession;

    public PartyPlayer(
            String deviceId,
            UserData playerUserSession,
            SpotifyAuthorizedClientService spotifyAuthorizedClientService,
            SpotifyPlayerService spotifyPlayerService,
            MessagingService messagingService,
            PartySession partySession
    ) {
        this.deviceId = deviceId;
        this.playerUserSession = playerUserSession;
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
        this.spotifyPlayerService = spotifyPlayerService;
        this.messagingService = messagingService;
        this.partySession = partySession;
        this.partyId = partySession.getPartyId();
        userToken = spotifyAuthorizedClientService.getAuthorizedClient(playerUserSession).getAccessToken().getTokenValue();
    }

    public synchronized boolean playNextTrack(boolean forceSkip) {
        AddedTrack nextAddedTrack = partyQueue.peekTrack();
        Track nextTrack = nextAddedTrack != null ? nextAddedTrack.track() : null;

        // if party queue is empty
        if (nextTrack == null) {
            currentlyPlaying = null;
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
                skipVotes.clear();
                messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
                messagingService.sendUpdate(partyId, new Message(MessageType.SKIP_VOTES_CHANGED, skipVotes.size()));
                return false;
            }
        }

        String newToken = spotifyAuthorizedClientService.getAuthorizedClient(playerUserSession).getAccessToken().getTokenValue();
        if (!newToken.equals(userToken)) {
            log.info("Detected token refresh for player in party {}, updating token", partyId);
            userToken = newToken;
            messagingService.sendPrivateUpdate(playerUserSession.getUserId(), MessageType.REFRESH_TOKEN);
        }

        boolean success = spotifyPlayerService.playTrack(
                userToken,
                nextTrack.getUri(),
                deviceId
        );

        if (success) {
            currentlyPlaying = nextAddedTrack;
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

        boolean skipped = handleSkipping(userId);
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

    public UUID getPlayerId() {
        return playerUserSession.getUserId();
    }
    public void decouplePlayerSession() {
        playerUserSession.setPartyId(null);
        playerUserSession.clearRoles();
        messagingService.sendPrivateUpdate(playerUserSession.getUserId(), MessageType.REFRESH_STATUS);
    }

    private boolean handleSkipping(UUID votingUserId) {
        PartySettings settings = partySession.getPartySettings();
        if (!settings.voteToSkip()) return false;

        boolean shouldSkip;
        int voteCount = skipVotes.size();

        if (settings.instantSelfSkip() && partyQueue.getCurrentlyPlayingUserId() == votingUserId) {
            // user want to skip his track and instantSelfSkip is on
            shouldSkip = true;
        } else {
            // normal skipping
            shouldSkip = fulfillThreshold(settings, voteCount);
        }

        if (shouldSkip) {
            playNextTrack(true);
            skipVotes.clear();
            log.info("Track skipped in party {} with {} skip votes", partyId, voteCount);
            return true;
        }

        return false;
    }
    private boolean fulfillThreshold(PartySettings settings, int voteCount) {
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