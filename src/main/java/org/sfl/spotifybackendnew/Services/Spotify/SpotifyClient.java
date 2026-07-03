package org.sfl.spotifybackendnew.Services.Spotify;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.Exceptions.SpotifyClientException;
import org.sfl.spotifybackendnew.SpotifyDTOs.ResponseDTOs.SpotifyGetUserPlaylistsResponse;
import org.sfl.spotifybackendnew.SpotifyDTOs.ResponseDTOs.SpotifySearchResponse;
import org.sfl.spotifybackendnew.SpotifyDTOs.SpotifyWrappersDTOs.SpotifyTrackWrapper;
import org.sfl.spotifybackendnew.SpotifyDTOs.SubDTOs.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class SpotifyClient {

    private final JsonMapper jsonMapper;
    private final RestTemplate restTemplate;
    private final SpotifyTokenService spotifyTokenService;
    private final String BASE_URL = "https://api.spotify.com/v1";

    public SpotifyClient(JsonMapper jsonMapper, RestTemplate restTemplate, SpotifyTokenService spotifyTokenService) {
        this.jsonMapper = jsonMapper;
        this.restTemplate = restTemplate;
        this.spotifyTokenService = spotifyTokenService;
    }

    // proxy service
    public SpotifyGetUserPlaylistsResponse getUserPlaylists(String accessToken) {
        try {
            String url = BASE_URL + "/me/playlists";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            return restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    SpotifyGetUserPlaylistsResponse.class
            ).getBody();

        } catch (Exception e) {
            throw new SpotifyClientException("Failed to fetch user playlists: " + e.getMessage(), e);
        }
    }
    public List<SpotifyTrack> getPlaylistItems(String accessToken, String playlistId, Integer offset) {
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(BASE_URL + "/playlists/" + playlistId + "/items")
                    .queryParam("limit", 50);

            if (offset != null) urlBuilder.queryParam("offset", offset);

            String url = urlBuilder.build().toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            JsonNode json = Objects.requireNonNull(restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            ).getBody());

            List<SpotifyTrackWrapper> spotifyTracksWrapped = jsonMapper.readerForListOf(SpotifyTrackWrapper.class).readValue(json.get("items"));
            return spotifyTracksWrapped.stream()
                    .map(SpotifyTrackWrapper::getTrack)
                    .toList();

        } catch (Exception e) {
            throw new SpotifyClientException("Failed to fetch playlist data: " + e.getMessage(), e);
        }
    }
    public SpotifyTrack getPlaylistItem(String accessToken, String playlistId, int index) {
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(BASE_URL + "/playlists/" + playlistId + "/items")
                    .queryParam("limit", 1)
                    .queryParam("offset", index);

            String url = urlBuilder.build().toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            JsonNode json = Objects.requireNonNull(restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            ).getBody());

            List<SpotifyTrackWrapper> spotifyTracksWrapped = jsonMapper.readerForListOf(SpotifyTrackWrapper.class).readValue(json.get("items"));
            return spotifyTracksWrapped.getFirst().getTrack();

        } catch (Exception e) {
            throw new SpotifyClientException("Failed to fetch playlist data: " + e.getMessage(), e);
        }
    }
    public SpotifySearchResponse search(String query) {
        try {
            String url = UriComponentsBuilder.fromUriString(BASE_URL + "/search")
                    .queryParam("q", query)
                    .queryParam("limit", 10)
                    .queryParam("type", "track")
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(spotifyTokenService.getApplicationToken());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            JsonNode json = Objects.requireNonNull(restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            ).getBody());

            SpotifySearchResponse response = new SpotifySearchResponse();

            List<SpotifyTrack> tracks = jsonMapper.readerForListOf(SpotifyTrack.class)
                    .readValue(json.get("tracks").get("items"));
            //todo albums and others containers

            response.setTracks(tracks);

            return response;

        } catch (Exception e) {
            throw new SpotifyClientException("Failed to perform search: " + e.getMessage(), e);
        }
    }
    public SpotifyTrack getTrack(String trackId) {
        try {
            String url = BASE_URL + "/tracks/" + trackId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(spotifyTokenService.getApplicationToken());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            return restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    SpotifyTrack.class
            ).getBody();

        } catch (Exception e) {
            throw new SpotifyClientException("Failed to fetch track: " + e.getMessage(), e);
        }
    }
    public int getPlaylistTotalTracks(String accessToken, String playlistId) {
        try {
            String url = BASE_URL + "/playlists/" + playlistId + "?fields=items(total)";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            JsonNode json = response.getBody();

            if (json == null) {
                log.warn("Total tracks for playlist body is null");
                return -1;
            }

            if (!json.has("items") || !json.get("items").has("total")) {
                log.warn("Failed to extract total track count for playlist {}: Invalid response format. JSON: {}", playlistId, json);
                return -1;
            }

            return json.get("items").get("total").asInt();

        } catch (Exception e) {
            throw new SpotifyClientException("Failed getting total number of tracks in playlist: " + e.getMessage(), e);
        }
    }

    // player service
    public void playTrack(String accessToken, String trackUri, String deviceId) {
        try {
            String url = BASE_URL + "/me/player/play?device_id=" + deviceId;
            Map<String, Object> body = Map.of("uris", List.of(trackUri));
            sendSpotifyPlayerRequest(accessToken, url, HttpMethod.PUT, body);
        } catch (Exception e) {
            throw new SpotifyClientException("Failed to play track: " + e.getMessage(), e);
        }
    }

    private void sendSpotifyPlayerRequest(String accessToken, String url, HttpMethod method, @Nullable Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity;
        if (body != null)
            entity = new HttpEntity<>(body, headers);
        else entity = new HttpEntity<>(headers);

        restTemplate.exchange(
                url,
                method,
                entity,
                Void.class
        );
    }
}