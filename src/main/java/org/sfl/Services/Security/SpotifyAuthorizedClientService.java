package org.sfl.Services.Security;

import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.User.UserData;
import org.sfl.Exceptions.SpotifyAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SpotifyAuthorizedClientService {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public SpotifyAuthorizedClientService(OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager) {
        this.authorizedClientManager = oAuth2AuthorizedClientManager;
    }


    public OAuth2AuthorizedClient getAuthorizedClient(UserData user) {
        if (user == null || !user.isSpotifyAuthenticated()) {
            return null;
        }

        String registrationId = user.isHasSpotifyPlayerPermissions() ? "spotify-host" : "spotify";

        String lock = user.getUserId().toString().intern();

        synchronized (lock) {
            Authentication technicalAuthentication = new UsernamePasswordAuthenticationToken(user.getUserId(), null, List.of(new SimpleGrantedAuthority("SPOTIFY_USER")));
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(registrationId)
                    .principal(technicalAuthentication)
                    .build();

            try {
                OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);

                if (client == null) {
                    throw new SpotifyAuthenticationException("Cannot find or refresh token for: " + registrationId);
                }

                return client;
            } catch (Exception e) {
                log.error("Error obtaining technical authorized client: {}", e.getMessage());
                throw new SpotifyAuthenticationException("Failed to obtain technical authorized client");
            }
        }
    }
}