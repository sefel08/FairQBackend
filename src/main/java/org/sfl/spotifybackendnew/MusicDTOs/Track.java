package org.sfl.spotifybackendnew.MusicDTOs;

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
    private List<String> artists;
    private String imageUrl;
    private long durationMs;
    private String spotifyUrl;
}