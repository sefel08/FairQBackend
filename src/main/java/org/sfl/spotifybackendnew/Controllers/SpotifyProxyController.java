package org.sfl.spotifybackendnew.Controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.sfl.spotifybackendnew.DTOs.Music.Playlist;
import org.sfl.spotifybackendnew.DTOs.Music.SearchResult;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.DTOs.Music.TrackContainerItem;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.sfl.spotifybackendnew.Services.Spotify.SpotifyProxyService;
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
    public SearchResult searchTracks(@RequestParam String query) {
        return spotifyProxyService.searchTracks(query);
    }

    @GetMapping("/user-playlists")
    public List<TrackContainerItem> getUserPlaylists(@AuthenticationPrincipal UserData user) {
        OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user);
        return spotifyProxyService.getUserPlaylists(authorizedClient);
    }

    @GetMapping("/artist")
    public List<TrackContainerItem> getArtistAlbums(@RequestParam String artistId) {
        return spotifyProxyService.getArtistAlbums(artistId);
    }

    @GetMapping("/playlist")
    public List<Track> getPlaylistTracks(@AuthenticationPrincipal UserData user, @RequestParam String playlistId, @RequestParam Integer offset) {
        OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user);
        return spotifyProxyService.getPlaylistTracks(authorizedClient, playlistId, offset);
    }

    @GetMapping("/album")
    public List<Track> getAlbumTracks(@RequestParam String albumId, @RequestParam Integer offset) {
        return spotifyProxyService.getAlbumTracks(albumId, offset);
    }
}