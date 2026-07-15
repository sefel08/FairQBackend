package org.sfl.SpotifyDTOs.ResponseDTOs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.sfl.SpotifyDTOs.SubDTOs.SpotifyAlbum;
import org.sfl.SpotifyDTOs.SubDTOs.SpotifyArtist;
import org.sfl.SpotifyDTOs.SubDTOs.SpotifyTrack;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifySearchResponse {
    @JsonIgnore
    private List<SpotifyTrack> tracks;
    @JsonIgnore
    private List<SpotifyAlbum> albums;
    @JsonIgnore
    private List<SpotifyArtist> artists;
}