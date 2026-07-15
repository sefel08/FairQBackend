package org.sfl.spotifybackendnew.DTOs.Music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult {
    private List<Track> tracks;
    private List<TrackContainerItem> albums;
}
