package org.sfl.spotifybackendnew.Services.Security;

import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Exceptions.SpotifyAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

@Service
public class SpotifyAuthorizedClientService {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public SpotifyAuthorizedClientService(OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager) {
        this.authorizedClientManager = oAuth2AuthorizedClientManager;
    }

    public OAuth2AuthorizedClient getAuthorizedClient(UserData user, Authentication authentication) {
        if (user == null || authentication == null) {
            return null;
        }

        if (!user.isSpotifyAuthenticated()) {
            return null;
        }

        String registrationId = user.isHasHostPermissions() ? "spotify-host" : "spotify";
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal(authentication)
                .build();

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);

        if (client == null) {
            throw new SpotifyAuthenticationException("Cannot find or refresh token for: " + registrationId);
        }

        return client;
    }
}