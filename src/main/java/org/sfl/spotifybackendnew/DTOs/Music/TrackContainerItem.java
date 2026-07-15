package org.sfl.spotifybackendnew.DTOs.Music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sfl.spotifybackendnew.Enums.TrackContainerType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrackContainerItem {
    private TrackContainerType type;
    private String id;
    private String name;
    private String imageUrl;
    private int totalTracks;
}
