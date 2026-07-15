package org.sfl.DTOs.Music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Artist {
    private String id;
    private String name;
    private String spotifyUrl;
    private String smallImageUrl;
    private String largeImageUrl;
}
