package org.sfl.DTOs.Music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sfl.Enums.TrackContainerType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrackContainerItem {
    private TrackContainerType type;
    private String id;
    private String name;
    private int totalTracks;
    private String smallImageUrl;
    private String largeImageUrl;
}
