package org.sfl.spotifybackendnew.Services.Spotify;

import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.Exceptions.SpotifyClientException;
import org.sfl.spotifybackendnew.Exceptions.SpotifyServiceException;
import org.sfl.spotifybackendnew.DTOs.Music.Playlist;
import org.sfl.spotifybackendnew.DTOs.Music.Track;
import org.sfl.spotifybackendnew.SpotifyDTOs.ResponseDTOs.SpotifyGetUserPlaylistsResponse;
import org.sfl.spotifybackendnew.SpotifyDTOs.ResponseDTOs.SpotifySearchResponse;
import org.sfl.spotifybackendnew.SpotifyDTOs.SubDTOs.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SpotifyProxyService {

    private final SpotifyClient spotifyClient;

    public SpotifyProxyService(SpotifyClient spotifyClient){
        this.spotifyClient = spotifyClient;
    }

    public Track getTrack(String trackId) {
        try {
            SpotifyTrack spotifyTrack = spotifyClient.getTrack(trackId);

            if (spotifyTrack == null) {
                return null;
            }

            return mapToTrackDTO(spotifyTrack);

        } catch (SpotifyClientException e) {
            log.error("Error fetching track: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to fetch track");
        } catch (Exception e) {
            log.error("Unexpected error fetching track: {}", e.getMessage());
            return null;
        }
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
            log.error("Error searching tracks: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to search tracks");
        } catch (Exception e) {
            log.error("Unexpected error searching tracks: {}", e.getMessage());
            return List.of();
        }
    }
    public List<Track> getPlaylistTracks(OAuth2AuthorizedClient authorizedClient, String playlistId, Integer offset) {
        try {
            String bearer = authorizedClient.getAccessToken().getTokenValue();
            List<SpotifyTrack> playlistItems = spotifyClient.getPlaylistItems(bearer, playlistId, offset);

            if (playlistItems == null || playlistItems.isEmpty()) {
                return List.of();
            }

            return playlistItems.stream()
                    .map(this::mapToTrackDTO)
                    .toList();

        } catch (SpotifyClientException e) {
            log.error("Error fetching playlist tracks: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to fetch playlist tracks");
        } catch (Exception e) {
            log.error("Unexpected error fetching playlist tracks: {}", e.getMessage());
            return List.of();
        }
    }
    public Track getPlaylistTrack(String token, String playlistId, Integer index) {
        try {
            SpotifyTrack playlistItem = spotifyClient.getPlaylistItem(token, playlistId, index);

            if (playlistItem == null) {
                return null;
            }

            return mapToTrackDTO(playlistItem);

        } catch (SpotifyClientException e) {
            log.error("Error fetching playlist tracks: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error fetching playlist tracks: {}", e.getMessage());
            return null;
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
            log.error("Error fetching user playlists: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to fetch user playlists");
        } catch (Exception e) {
            log.error("Unexpected error fetching user playlists: {}", e.getMessage());
            return List.of();
        }
    }
    public int getTotalNumberOfTracksInPlaylist(OAuth2AuthorizedClient authorizedClient, String playlistId) {
        try {
            String bearer = authorizedClient.getAccessToken().getTokenValue();
            return spotifyClient.getPlaylistTotalTracks(bearer, playlistId);
        } catch (SpotifyClientException e) {
            log.error("Error getting total number of tracks in playlist: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to get total number of tracks in playlist");
        } catch (Exception e) {
            log.error("Unexpected error getting total number of tracks in playlist: {}", e.getMessage());
            return -1;
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
                track.getSpotifyUrl(),
                track.getUri()
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