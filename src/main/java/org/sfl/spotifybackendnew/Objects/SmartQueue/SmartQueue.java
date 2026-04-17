package org.sfl.spotifybackendnew.Objects.SmartQueue;

import org.jspecify.annotations.NonNull;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.Objects.Party.PartyUser;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class SmartQueue {

    private final Map<UUID, PartyUser> userMap;
    private final CopyOnWriteArrayList<UUID> joinOrder;
    private int playingUserIndex = 0;

    private final AtomicReference<List<AddedTrack>> cachedQueue = new AtomicReference<>(new ArrayList<>());

    public SmartQueue(Map<UUID, PartyUser> userMap, CopyOnWriteArrayList<UUID> joinOrder) {
        this.userMap = userMap;
        this.joinOrder = joinOrder;
    }

    public List<AddedTrack> getQueue() {
        return cachedQueue.get();
    }

    record UserTrack(Track track, int index, int userIndex, PartyUser partyUser) {}
    public void refreshQueue() {
        List<UUID> currentOrder = List.copyOf(joinOrder);
        List<UserTrack> allTracks = getAllUserTracks(currentOrder);

        // sort tracks by index and joinOrder
        int currentPlayingUserIndex = playingUserIndex;
        allTracks.sort(Comparator.comparingInt((UserTrack ut) -> ut.index)
                .thenComparingInt(ut -> (ut.userIndex < currentPlayingUserIndex) ? currentPlayingUserIndex + ut.userIndex : ut.userIndex - currentPlayingUserIndex));

        // change Tracks to AddedTrack
        List<AddedTrack> newCalculatedQueue = allTracks.stream()
                .map(ut -> new AddedTrack(ut.track, ut.partyUser().getProfile()))
                .toList();

        updateCache(newCalculatedQueue);
    }

    private void updateCache(List<AddedTrack> newCalculatedQueue) {
        cachedQueue.set(List.copyOf(newCalculatedQueue));
    }
    private @NonNull List<UserTrack> getAllUserTracks(List<UUID> currentOrder) {
        Map<UUID, Integer> userIndexes = new HashMap<>();
        for (int i = 0; i < currentOrder.size(); i++) {
            userIndexes.put(currentOrder.get(i), i);
        }

        // collect all user queues
        List<UserTrack> allTracks = new ArrayList<>();
        for (PartyUser user : userMap.values()) {
            int userIndex = userIndexes.get(user.getId());
            List<Track> userQueue = user.getQueue();
            for (int i = 0; i < userQueue.size(); i++) {
                allTracks.add(new UserTrack(userQueue.get(i), i, userIndex, user));
            }
        }

        return allTracks;
    }
}