package org.sfl.spotifybackendnew.Objects.SmartQueue;

import lombok.Getter;
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

    @Getter
    private UUID currentlyPlayingUserId = null;
    private int currentlyPlayingIndex = 0;

    // only for displaying whole queue
    private final AtomicReference<List<AddedTrack>> cachedQueue = new AtomicReference<>(new ArrayList<>());

    public SmartQueue(Map<UUID, PartyUser> userMap, CopyOnWriteArrayList<UUID> joinOrder) {
        this.userMap = userMap;
        this.joinOrder = joinOrder;
    }

    public List<AddedTrack> getQueue() {
        refreshQueue();
        return cachedQueue.get();
    }

    // returns userID of the user whose track was removed
    public UUID pollTrack() {
        int size = joinOrder.size();
        if (size == 0) return null;

        int currentPlayingUserIndex = getCurrentPlayingUserIndex();

        for (int i = 0; i < size; i++) {
            UUID userId = joinOrder.get(currentPlayingUserIndex);
            PartyUser currentUser = userMap.get(userId);

            if (currentUser != null) {
                List<Track> currentUserQueue = currentUser.getQueue();
                if (!currentUserQueue.isEmpty()) {
                    currentlyPlayingUserId = userId;
                    currentlyPlayingIndex = currentPlayingUserIndex;
                    currentUser.removeTrack(0);
                    return userId;
                }
            }

            currentPlayingUserIndex = (currentPlayingUserIndex + 1) % size;
        }

        return null;
    }
    public AddedTrack peekTrack() {
        int size = joinOrder.size();
        if (size == 0) return null;

        int currentPlayingUserIndex = getCurrentPlayingUserIndex();

        int tempIndex = currentPlayingUserIndex;
        for (int i = 0; i < size; i++) {
            UUID userId = joinOrder.get(tempIndex);
            PartyUser currentUser = userMap.get(userId);

            if (currentUser != null) {
                List<Track> currentUserQueue = currentUser.getQueue();
                if (!currentUserQueue.isEmpty()) {
                    return new AddedTrack(currentUserQueue.getFirst(), currentUser.getProfile());
                }
            }

            tempIndex = (tempIndex + 1) % size;
        }

        return null;
    }

    record UserTrack(Track track, int index, int userIndex, PartyUser partyUser) {}
    public void refreshQueue() {
        List<UUID> currentOrder = List.copyOf(joinOrder);
        List<UserTrack> allTracks = getAllUserTracks(currentOrder);

        // sort tracks by index and joinOrder
        int currentPlayingUserIndex = getCurrentPlayingUserIndex();
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
    private int getCurrentPlayingUserIndex() {
        int size = joinOrder.size();
        if (size == 0) return 0;
        int currentPlayingUserIndex = 0;
        if (currentlyPlayingUserId != null) {
            int actualIndexInList = joinOrder.indexOf(currentlyPlayingUserId);
            if (actualIndexInList != -1) {
                currentPlayingUserIndex = (actualIndexInList + 1) % size;
            } else {
                currentPlayingUserIndex = currentlyPlayingIndex % size;
            }
        }
        return currentPlayingUserIndex;
    }
}