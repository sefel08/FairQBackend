package org.sfl.SpotifyDTOs.ResponseDTOs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.sfl.SpotifyDTOs.SubDTOs.SpotifyAlbum;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyGetArtistAlbumsResponse {
    @JsonProperty("items")
    private List<SpotifyAlbum> albums;
}
