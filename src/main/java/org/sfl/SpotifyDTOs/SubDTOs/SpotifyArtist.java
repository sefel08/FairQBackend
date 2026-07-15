package org.sfl.SpotifyDTOs.SubDTOs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyArtist {
    private String id;
    private String name;
    private List<SpotifyImage> images;

    @JsonProperty("href")
    private String spotifyUrl;
}
