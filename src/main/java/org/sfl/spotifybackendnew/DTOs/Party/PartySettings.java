package org.sfl.spotifybackendnew.DTOs.Party;

public record PartySettings(
        boolean voteToSkip,
        boolean percentVoting,
        boolean moreThanThreshold, // decides if there must be moreThanThreshold votes than threshold to skip
        double voteThreshold
) {}