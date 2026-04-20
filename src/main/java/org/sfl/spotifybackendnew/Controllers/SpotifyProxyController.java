package org.sfl.spotifybackendnew.Controllers;

import org.sfl.spotifybackendnew.DTOs.Music.Playlist;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyProxyService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spotify")
public class SpotifyProxyController {

    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;
    private final SpotifyProxyService spotifyProxyService;

    public SpotifyProxyController(SpotifyAuthorizedClientService spotifyAuthorizedClientService, SpotifyProxyService spotifyProxyService) {
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
        this.spotifyProxyService = spotifyProxyService;
    }

    @GetMapping("/search")
    public List<Track> searchTracks(@RequestParam String query) {
        return spotifyProxyService.searchTracks(query);
    }

    @GetMapping("/user-playlists")
    public List<Playlist> getUserPlaylists(@AuthenticationPrincipal UserData user, Authentication authentication) {
        OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user, authentication);
        return spotifyProxyService.getUserPlaylists(authorizedClient);
    }

    @GetMapping("/playlist")
    public List<Track> getPlaylistTracks(@AuthenticationPrincipal UserData user, Authentication authentication, @RequestParam String playlistId) {
        OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user, authentication);
        return spotifyProxyService.getPlaylistTracks(authorizedClient, playlistId);
    }
}