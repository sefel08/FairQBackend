package org.sfl.spotifybackendnew.Services.Security;

import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Exceptions.SpotifyAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

@Service
public class SpotifyAuthorizedClientService {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public SpotifyAuthorizedClientService(OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {
        this.authorizedClientService = oAuth2AuthorizedClientService;
    }

    public OAuth2AuthorizedClient getAuthorizedClient(UserData user, Authentication authentication) {
        if (user == null || authentication == null) {
            throw new SpotifyAuthenticationException("No user session available");
        }

        String registrationId = user.isHasHostPermissions() ? "spotify-host" : "spotify";
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(registrationId, authentication.getName());

        if (client == null) {
            throw new SpotifyAuthenticationException("Cannot find token for: " + registrationId);
        }

        return client;
    }
}
