package org.sfl.DTOs.Music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Track {
    private String id;
    private String name;
    private List<SimpleArtist> artists;
    private long durationMs;
    private String spotifyUrl;
    private String uri;
    private String smallImageUrl;
    private String largeImageUrl;
}