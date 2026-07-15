package org.sfl.SpotifyDTOs.ResponseDTOs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.sfl.SpotifyDTOs.SubDTOs.SpotifyPlaylist;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyGetUserPlaylistsResponse {
    @JsonProperty("items")
    private List<SpotifyPlaylist> playlists;
}