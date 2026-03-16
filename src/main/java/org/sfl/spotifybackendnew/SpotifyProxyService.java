package org.sfl.spotifybackendnew;

import org.sfl.spotifybackendnew.Exceptions.SpotifyClientException;
import org.sfl.spotifybackendnew.Exceptions.SpotifyServiceException;
import org.sfl.spotifybackendnew.MusicDTOs.Playlist;
import org.sfl.spotifybackendnew.MusicDTOs.Track;
import org.sfl.spotifybackendnew.SpotifyDTOs.ResponseDTOs.SpotifyGetUserPlaylistsResponse;
import org.sfl.spotifybackendnew.SpotifyDTOs.ResponseDTOs.SpotifySearchResponse;
import org.sfl.spotifybackendnew.SpotifyDTOs.SubDTOs.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SpotifyProxyService {

    private final SpotifyClient spotifyClient;

    public SpotifyProxyService(SpotifyClient spotifyClient){
        this.spotifyClient = spotifyClient;
    }

    public List<Track> searchTracks(String query) {
        try {
            SpotifySearchResponse response = spotifyClient.search(query);

            //todo check other containers
            if (response == null || response.getTracks() == null) {
                return List.of();
            }

            return response.getTracks().stream()
                    .map(this::mapToTrackDTO)
                    .toList();

        } catch (SpotifyClientException e) {
            System.err.println("Error searching tracks: " + e.getMessage());
            throw new SpotifyServiceException("Failed to search tracks");
        } catch (Exception e) {
            System.err.println("Unexpected error searching tracks: " + e.getMessage());
            return List.of();
        }
    }
    public List<Track> getPlaylistTracks(OAuth2AuthorizedClient authorizedClient, String playlistId) {
        try {
            String bearer = authorizedClient.getAccessToken().getTokenValue();
            SpotifyPlaylist playlist = spotifyClient.getPlaylistData(bearer, playlistId);

            if (playlist == null || playlist.getTracks() == null) {
                return List.of();
            }

            return playlist.getTracks().stream()
                    .map(this::mapToTrackDTO)
                    .toList();

        } catch (SpotifyClientException e) {
            System.err.println("Error fetching playlist tracks: " + e.getMessage());
            throw new SpotifyServiceException("Failed to fetch playlist tracks");
        } catch (Exception e) {
            System.err.println("Unexpected error fetching playlist tracks: " + e.getMessage());
            return List.of();
        }
    }
    public List<Playlist> getUserPlaylists(OAuth2AuthorizedClient authorizedClient) {
        try {
            String bearer = authorizedClient.getAccessToken().getTokenValue();
            SpotifyGetUserPlaylistsResponse response = spotifyClient.getUserPlaylists(bearer);

            if (response == null || response.getPlaylists() == null) {
                return List.of();
            }

            return response.getPlaylists().stream()
                    .map(this::mapToPlaylistDTO)
                    .toList();

        } catch (SpotifyClientException e) {
            System.err.println("Error fetching user playlists: " + e.getMessage());
            throw new SpotifyServiceException("Failed to fetch user playlists");
        } catch (Exception e) {
            System.err.println("Unexpected error fetching user playlists: " + e.getMessage());
            return List.of();
        }
    }

    //helper methods
    private Track mapToTrackDTO(SpotifyTrack track) {
        return new Track(
                track.getId(),
                track.getName(),
                getArtistNames(track.getArtists()),
                getImageUrl(track.getImages()),
                track.getDurationMs(),
                track.getSpotifyUrl()
        );
    }
    private Playlist mapToPlaylistDTO(SpotifySimplePlaylist playlist) {
        return new Playlist(
                playlist.getId(),
                playlist.getName(),
                getImageUrl(playlist.getImages()),
                playlist.getTrackCount()
        );
    }

    private List<String> getArtistNames(List<SpotifyArtist> spotifyArtistList) {
        return Optional.ofNullable(spotifyArtistList)
                .filter(artists -> !artists.isEmpty())
                .map(artists -> artists.stream().map(SpotifyArtist::getName).toList())
                .orElse(List.of("Unknown Artist"));
    }
    private String getImageUrl(List<SpotifyImage> spotifyImageList) {
        return Optional.ofNullable(spotifyImageList)
                .filter(images -> !images.isEmpty())
                .map(images -> images.getFirst().getUrl())
                .orElse("https://link-do-domyslnej-okladki.png");
    }
}