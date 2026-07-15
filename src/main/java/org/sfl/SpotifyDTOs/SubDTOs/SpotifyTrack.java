package org.sfl.SpotifyDTOs.SubDTOs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyTrack {
    private String id;
    private String name;
    private List<SpotifyArtist> artists;
    @JsonProperty("duration_ms")
    private long durationMs;
    @JsonProperty("href")
    private String spotifyUrl;
    private String uri;

    public List<SpotifyImage> getImages() {
        if (albumObject == null) {
            return null;
        }
        return albumObject.getImages();
    }

    @JsonSetter("album")
    private SpotifySimpleAlbum albumObject;
}