package org.sfl.Controllers;

import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.User.UserData;
import org.sfl.Services.Security.SpotifyAuthorizedClientService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class AuthController {

    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;

    public AuthController(SpotifyAuthorizedClientService spotifyAuthorizedClientService) {
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
    }


    @GetMapping("/status")
    public Map<String, Object> checkStatus(@AuthenticationPrincipal UserData user) {
        // doesn't have session created
        if (user == null) {
            return Map.of("isLoggedIn", false);
        }

        Map<String, Object> status = new HashMap<>();
        status.put("isLoggedIn", true);
        status.put("isSpotifyAuthenticated", user.isSpotifyAuthenticated());
        status.put("isPremium", user.isPremium());
        status.put("hasHostPermissions", user.isHasSpotifyPlayerPermissions());
        status.put("displayName", user.getDisplayName());
        status.put("imageUrl", user.getImageUrl());
        status.put("smallImageUrl", user.getSmallImageUrl());
        status.put("isUser", user.isUser());
        status.put("isPlayer", user.isPlayer());
        status.put("isHost", user.isHost());

        return status;
    }

    @GetMapping("/spotify-token")
    public Map<String, Object> getSpotifyToken(@AuthenticationPrincipal UserData user) {
        Map<String, Object> responseBody = new HashMap<>();

        log.info("Getting Spotify token for user: {}", user.getUsername());
        OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user);
        String spotifyUserToken = null;
        if (authorizedClient != null) {
            spotifyUserToken = authorizedClient.getAccessToken().getTokenValue();
        } else if (user.isSpotifyAuthenticated()) {
            // user should have token but has not, probably session expired or something, needs to reauthenticate
            responseBody.put("needsReauth", true);
        }
        responseBody.put("spotifyUserToken", spotifyUserToken);

        return responseBody;
    }
}