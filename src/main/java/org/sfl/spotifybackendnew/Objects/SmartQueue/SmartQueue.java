package org.sfl.spotifybackendnew.Objects.SmartQueue;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.sfl.spotifybackendnew.DTOs.Music.AddedTrack;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.Objects.Party.PartyUser;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class SmartQueue {

    private final Map<UUID, PartyUser> userMap;
    private final CopyOnWriteArrayList<UUID> joinOrder;

    @Getter
    private UUID currentlyPlayingUserId = null;
    private int currentlyPlayingIndex = 0;

    // only for displaying whole queue
    private final AtomicReference<LinkedHashMap<UUID, AddedTrack>> cachedQueue = new AtomicReference<>(new LinkedHashMap<>());
    private boolean queueNeedsRecalculation = false;

    public SmartQueue(Map<UUID, PartyUser> userMap, CopyOnWriteArrayList<UUID> joinOrder) {
        this.userMap = userMap;
        this.joinOrder = joinOrder;
    }

    public Map<UUID, AddedTrack> getQueue() {
        if (queueNeedsRecalculation) {
            log.info("Queue needs recalculation, refreshing...");
            refreshQueue();
        }
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
                Map<UUID, Track> currentUserQueue = currentUser.getQueue();
                if (!currentUserQueue.isEmpty()) {
                    currentlyPlayingUserId = userId;
                    currentlyPlayingIndex = currentPlayingUserIndex;
                    currentUser.removeFirstTrack();
                    partyQueueChanged();
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
                LinkedHashMap<UUID, Track> currentUserQueue = currentUser.getQueue();
                if (!currentUserQueue.isEmpty()) {
                    return new AddedTrack(currentUserQueue.pollFirstEntry().getValue(), currentUser.getProfile());
                }
            }

            tempIndex = (tempIndex + 1) % size;
        }

        return null;
    }

    public void partyQueueChanged() {
        queueNeedsRecalculation = true;
    }

    record UserTrack(Track track, UUID queueItemId, int index, int userIndex, PartyUser partyUser) {}
    private void refreshQueue() {
        List<UUID> currentOrder = List.copyOf(joinOrder);
        List<UserTrack> allTracks = getAllUserTracks(currentOrder);

        // sort tracks by index and joinOrder
        int currentPlayingUserIndex = getCurrentPlayingUserIndex();
        allTracks.sort(Comparator.comparingInt((UserTrack ut) -> ut.index)
                .thenComparingInt(ut -> (ut.userIndex < currentPlayingUserIndex) ? currentPlayingUserIndex + ut.userIndex : ut.userIndex - currentPlayingUserIndex));

        // change List to Map with AddedTrack
        LinkedHashMap<UUID, AddedTrack> newCalculatedQueue = new LinkedHashMap<>();
        for (UserTrack ut: allTracks) {
            newCalculatedQueue.put(ut.queueItemId, new AddedTrack(ut.track, ut.partyUser.getProfile()));
        }

        updateCache(newCalculatedQueue);
        queueNeedsRecalculation = false;
    }

    private void updateCache(LinkedHashMap<UUID, AddedTrack> newCalculatedQueue) {
        cachedQueue.set(newCalculatedQueue);
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
            LinkedHashMap<UUID, Track> userQueue = user.getQueue();
            int index = 0;
            for (Map.Entry<UUID, Track> entry : userQueue.entrySet()) {
                allTracks.add(new UserTrack(entry.getValue(), entry.getKey(), index, userIndex, user));
                index++;
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