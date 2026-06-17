package org.sfl.spotifybackendnew.DTOs.Party;

import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;

import java.util.List;

public record PartyQueueInfo(List<AddedTrack> queue, AddedTrack currentlyPlaying) {}