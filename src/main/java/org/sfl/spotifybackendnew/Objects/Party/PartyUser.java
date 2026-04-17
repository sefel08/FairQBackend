package org.sfl.spotifybackendnew.Objects.Party;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.User.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class PartyUser {
    private final UUID id;

    // display info
    private UserProfile profile;

    @Getter(AccessLevel.NONE)
    private final List<Track> queue = new ArrayList<>();

    public PartyUser(UUID userId, UserProfile profile) {
        id = userId;
        this.profile = profile;
    }

    public synchronized void addTrack(Track track) {
        queue.add(track);
    }
    public synchronized void removeTrack(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
        }
    }
    public synchronized List<Track> getQueue() {
        return new ArrayList<>(queue);
    }
}