package org.sfl.spotifybackendnew.SpotifyDTOs.SubDTOs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyAlbum {
    private String id;
    private String name;
    private List<SpotifyImage> images;
    private Integer total_tracks;

    @JsonProperty("href")
    private String spotifyUrl;
}
