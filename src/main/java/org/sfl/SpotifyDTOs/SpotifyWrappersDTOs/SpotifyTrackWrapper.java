package org.sfl.SpotifyDTOs.SpotifyWrappersDTOs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.sfl.SpotifyDTOs.SubDTOs.SpotifyTrack;

@Data
public class SpotifyTrackWrapper {
    @JsonProperty("item")
    private SpotifyTrack track;
}
