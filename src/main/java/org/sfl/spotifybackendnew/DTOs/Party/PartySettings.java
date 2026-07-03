package org.sfl.spotifybackendnew.DTOs.Party;

public record PartySettings(
        boolean voteToSkip,
        boolean percentVoting,
        boolean moreThanThreshold, // decides if there must be moreThanThreshold votes than threshold to skip
        double voteThreshold,
        boolean instantSelfSkip,
        String fallbackPlaylistId // playlist to use when queue is empty. Null in case of not using this function.
) {}