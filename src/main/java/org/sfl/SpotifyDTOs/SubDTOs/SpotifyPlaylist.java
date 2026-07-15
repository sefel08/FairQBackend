package org.sfl.SpotifyDTOs.SubDTOs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyPlaylist {
    private String id;
    private String name;
    private List<SpotifyImage> images;
    @JsonProperty("href")
    private String spotifyUrl;
    private Integer trackCount;

    @JsonProperty("items")
    private void unwrapper(JsonNode itemsObject) {
        trackCount = itemsObject.get("total").asInt();
    }
}