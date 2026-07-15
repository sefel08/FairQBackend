package org.sfl.DTOs.Party;

import org.sfl.DTOs.Music.AddedTrack;

import java.util.Map;
import java.util.UUID;

public record PartyQueueInfo(Map<UUID, AddedTrack> queue, AddedTrack currentlyPlaying) {}