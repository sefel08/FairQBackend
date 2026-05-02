package org.sfl.spotifybackendnew.Controllers;

import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Services.Security.SpotifyAuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final SpotifyAuthorizedClientService spotifyAuthorizedClientService;

    public AuthController(SpotifyAuthorizedClientService spotifyAuthorizedClientService) {
        this.spotifyAuthorizedClientService = spotifyAuthorizedClientService;
    }


    @GetMapping("/status")
    public Map<String, Object> checkStatus(@AuthenticationPrincipal UserData user, Authentication authentication) {
        // doesn't have session created
        if (user == null) {
            return Map.of("isLoggedIn", false);
        }

        Map<String, Object> status = new HashMap<>();
        status.put("isLoggedIn", true);
        status.put("isSpotifyAuthenticated", user.isSpotifyAuthenticated());
        status.put("isPremium", user.isPremium());
        status.put("hasHostPermissions", user.isHasHostPermissions());
        status.put("displayName", user.getDisplayName());
        status.put("imageUrl", user.getImageUrl());
        status.put("smallImageUrl", user.getSmallImageUrl());

        OAuth2AuthorizedClient authorizedClient = spotifyAuthorizedClientService.getAuthorizedClient(user, authentication);
        String spotifyUserToken = null;
        if (authorizedClient != null) {
            spotifyUserToken = authorizedClient.getAccessToken().getTokenValue();
        } else if (user.isSpotifyAuthenticated()) {
            // user should have token but has nott, probably session expired or something, needs to reauthenticate
            status.put("needsReauth", true);
        }
        status.put("spotifyUserToken", spotifyUserToken);

        return status;
    }
}