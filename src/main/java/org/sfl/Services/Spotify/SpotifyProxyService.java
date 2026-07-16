package org.sfl.Services.Spotify;

import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.Music.*;
import org.sfl.Enums.TrackContainerType;
import org.sfl.Exceptions.SpotifyClientException;
import org.sfl.Exceptions.SpotifyServiceException;
import org.sfl.SpotifyDTOs.ResponseDTOs.SpotifyGetArtistAlbumsResponse;
import org.sfl.SpotifyDTOs.ResponseDTOs.SpotifyGetUserPlaylistsResponse;
import org.sfl.SpotifyDTOs.ResponseDTOs.SpotifySearchResponse;
import org.sfl.SpotifyDTOs.SubDTOs.*;
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
    public SearchResult searchTracks(String query) {
        try {
            SpotifySearchResponse response = spotifyClient.search(query);

            if (response == null) {
                return new SearchResult(List.of(), List.of(), List.of());
            }

            List<Track> tracks = Optional.ofNullable(response.getTracks())
                    .orElse(List.of())
                    .stream()
                    .map(this::mapToTrackDTO)
                    .toList();

            List<TrackContainerItem> albums = Optional.ofNullable(response.getAlbums())
                    .orElse(List.of())
                    .stream()
                    .map(this::mapToAlbumDTO)
                    .toList();

            List<Artist> artists = Optional.ofNullable(response.getArtists())
                    .orElse(List.of())
                    .stream()
                    .map(this::mapToArtistDTO)
                    .toList();

            return new SearchResult(tracks, albums, artists);

        } catch (SpotifyClientException e) {
            log.error("Unexpected error searching tracks", e);
            throw new SpotifyServiceException("Failed to search tracks");
        } catch (Exception e) {
            log.error("Unexpected error searching tracks: {}", e.getMessage());
            return new SearchResult(List.of(), List.of(), List.of());
        }
    }
    public List<Track> getPlaylistTracks(OAuth2AuthorizedClient authorizedClient, String playlistId, Integer offset) {
        try {
            String bearer = authorizedClient.getAccessToken().getTokenValue();
            List<SpotifyTrack> playlistTracks = spotifyClient.getPlaylistTracks(bearer, playlistId, offset);

            if (playlistTracks == null || playlistTracks.isEmpty()) {
                return List.of();
            }

            return playlistTracks.stream()
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
    public List<Track> getAlbumTracks(String albumId, Integer offset) {
        try {
            List<SpotifyImage> albumImages = spotifyClient.getAlbumImages(albumId);
            List<SpotifyTrack> albumTracks = spotifyClient.getAlbumTracks(albumId, offset);

            if (albumTracks == null || albumTracks.isEmpty()) {
                return List.of();
            }

            return albumTracks.stream()
                    .map(track -> mapToTrackDTO(track, albumImages))
                    .toList();

        } catch (SpotifyClientException e) {
            log.error("Error fetching album tracks: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to fetch album tracks");
        } catch (Exception e) {
            log.error("Unexpected error fetching album tracks: {}", e.getMessage());
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
    public List<TrackContainerItem> getUserPlaylists(OAuth2AuthorizedClient authorizedClient) {
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
            log.error("Error fetching user playlists: {}", e.getLocalizedMessage());
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
    public Artist getArtist(String artistId) {
        try {
            SpotifyArtist spotifyArtist = spotifyClient.getArtist(artistId);

            if (spotifyArtist == null) {
                return null;
            }

            return mapToArtistDTO(spotifyArtist);

        } catch (SpotifyClientException e) {
            log.error("Error fetching artist: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to fetch artist");
        } catch (Exception e) {
            log.error("Unexpected error fetching artist: {}", e.getMessage());
            return null;
        }
    }
    public List<TrackContainerItem> getArtistAlbums(String artistId) {
        try {
            SpotifyGetArtistAlbumsResponse response = spotifyClient.getArtistAlbums(artistId);

            if (response == null || response.getAlbums() == null) {
                return List.of();
            }

            return response.getAlbums().stream()
                    .map(this::mapToAlbumDTO)
                    .toList();

        } catch (SpotifyClientException e) {
            log.error("Error fetching artist albums: {}", e.getMessage());
            throw new SpotifyServiceException("Failed to fetch artist albums");
        } catch (Exception e) {
            log.error("Unexpected error fetching artist albums: {}", e.getMessage());
            return List.of();
        }
    }

    //helper methods
    private Track mapToTrackDTO(SpotifyTrack track) {
        return new Track(
            track.getId(),
            track.getName(),
            mapToSimpleArtistList(track.getArtists()),
            track.getDurationMs(),
            track.getSpotifyUrl(),
            track.getUri(),
            getSmallImageUrl(track.getImages()),
            getLargeImageUrl(track.getImages())
        );
    }
    private Track mapToTrackDTO(SpotifyTrack track, List<SpotifyImage> images) {
        return new Track(
            track.getId(),
            track.getName(),
            mapToSimpleArtistList(track.getArtists()),
            track.getDurationMs(),
            track.getSpotifyUrl(),
            track.getUri(),
            getSmallImageUrl(images),
            getLargeImageUrl(images)
        );
    }
    private TrackContainerItem mapToPlaylistDTO(SpotifyPlaylist playlist) {
        return new TrackContainerItem(
                TrackContainerType.PLAYLIST,
                playlist.getId(),
                playlist.getName(),
                playlist.getTrackCount(),
                getSmallImageUrl(playlist.getImages()),
                getLargeImageUrl(playlist.getImages())
        );
    }
    private TrackContainerItem mapToAlbumDTO(SpotifyAlbum album) {
        return new TrackContainerItem(
                TrackContainerType.ALBUM,
                album.getId(),
                album.getName(),
                album.getTotal_tracks(),
                getSmallImageUrl(album.getImages()),
                getLargeImageUrl(album.getImages())
        );
    }
    private Artist mapToArtistDTO(SpotifyArtist artist) {
        return new Artist(
                artist.getId(),
                artist.getName(),
                artist.getSpotifyUrl(),
                getSmallImageUrl(artist.getImages()),
                getLargeImageUrl(artist.getImages())
        );
    }

    private List<SimpleArtist> mapToSimpleArtistList(List<SpotifyArtist> spotifyArtistList) {
        return Optional.ofNullable(spotifyArtistList)
                .map(artists -> artists.stream().map(this::mapToSimpleArtist).toList())
                .orElse(List.of());
    }
    private SimpleArtist mapToSimpleArtist(SpotifyArtist artist) {
        return new SimpleArtist(
                artist.getId(),
                artist.getName()
        );
    }

    private String getSmallImageUrl(List<SpotifyImage> spotifyImages) {
        if (spotifyImages == null || spotifyImages.isEmpty()) {
            return null;
        }
        return spotifyImages.getLast().getUrl();
    }
    private String getLargeImageUrl(List<SpotifyImage> spotifyImages) {
        if (spotifyImages == null || spotifyImages.isEmpty()) {
            return null;
        }
        return spotifyImages.getFirst().getUrl();
    }
}