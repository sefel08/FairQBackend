package org.sfl.spotifybackendnew.DTOs.Party;

public record PartySettings(
        boolean voteToSkip,
        boolean percentVoting,
        double voteThreshold
) {}