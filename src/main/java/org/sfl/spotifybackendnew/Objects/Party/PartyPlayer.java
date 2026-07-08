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
        // Poll from queue
        AddedTrack nextAddedTrack = partyQueue.peekTrack();
        boolean queueIsEmpty = nextAddedTrack == null;
        boolean forceSkipped = false;
        boolean success = false;
        boolean noTrackFound = false;

        // Next poll from fallback playlist
        if (nextAddedTrack == null && partySession.getPartySettings().fallbackPlaylistId() != null) {
            nextAddedTrack = partySession.pollFallbackTrack(getFreshToken());
        }
        // If force to skip play 1 second of silence to instantly end current track
        if (nextAddedTrack == null && forceSkip) {
            forceSkipped = true;
            nextAddedTrack = new AddedTrack(new Track(
                    "4jaXxB0DJ6X4PdjMK8XVfu",
                    "",
                    List.of(),
                    "",
                    0,
                    "https://open.spotify.com/track/4jaXxB0DJ6X4PdjMK8XVfu",
                    "spotify:track:4jaXxB0DJ6X4PdjMK8XVfu"
            ), null);
        }

        if (nextAddedTrack != null) {
            // We have a track to play
            success = spotifyPlayerService.playTrack(
                    getFreshToken(),
                    nextAddedTrack.track().getUri(),
                    deviceId
            );
        } else {
            noTrackFound = true;
        }

        if (forceSkipped) { // skipped to silence
            log.info("Skipped to silence in party {}", partyId);
            currentlyPlaying = null;
            messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
            waitsForNewTrack.set(true);
            return true;
        } else if (success) { // played new track
            log.info("Successfully played track {}", nextAddedTrack.track().getUri());
            currentlyPlaying = nextAddedTrack;
            messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
            waitsForNewTrack.set(false);
            if (!queueIsEmpty) {
                UUID userWhoPlayed = partyQueue.pollTrack();
                messagingService.sendPrivateUpdate(userWhoPlayed, MessageType.REFRESH_QUEUE);
            }
            return true;
        } else if (noTrackFound) { // last track ended
            log.info("No track found to play next. Waiting for new track");
            currentlyPlaying = null;
            messagingService.sendUpdate(partyId, MessageType.PARTY_QUEUE_CHANGED);
            waitsForNewTrack.set(true);
            return true;
        } else { // something went wrong. Probably we couldn't play next track.
            log.warn("Failed to play track for party {}. This should not be possible.", partyId);
            waitsForNewTrack.set(true);
            currentlyPlaying = null;
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
        if (!skipped)
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
            messagingService.sendUpdate(partyId, new Message(MessageType.SKIP_VOTES_CHANGED, 0));
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
    private String getFreshToken() {
        String newToken = spotifyAuthorizedClientService.getAuthorizedClient(playerUserSession).getAccessToken().getTokenValue();
        if (!newToken.equals(userToken)) {
            log.info("Detected token refresh for player in party {}, updating token", partyId);
            userToken = newToken;
            messagingService.sendPrivateUpdate(playerUserSession.getUserId(), MessageType.REFRESH_TOKEN);
        }
        return userToken;
    }
}