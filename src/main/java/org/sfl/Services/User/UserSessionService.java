package org.sfl.Services.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.User.UserData;
import org.sfl.Services.Party.PartyService;
import org.sfl.SpotifyDTOs.SubDTOs.SpotifyImage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@Slf4j
@Service
public class UserSessionService {

    private final JsonMapper mapper;
    private final PartyService partyService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SecurityContextLogoutHandler logoutHandler;

    public UserSessionService(JsonMapper mapper, PartyService partyService, OAuth2AuthorizedClientService authorizedClientService) {
        this.mapper = mapper;
        this.partyService = partyService;
        this.authorizedClientService = authorizedClientService;
        this.logoutHandler = new SecurityContextLogoutHandler();
        this.logoutHandler.setInvalidateHttpSession(true);
        this.logoutHandler.setClearAuthentication(true);
    }

    public void initializeSessionForGuest(HttpServletRequest request, HttpServletResponse response, String displayName) {
        UserData userData = new UserData(
                UUID.randomUUID(),
                displayName,
                null,
                false,
                false,
                false,
                null,
                null,
                null
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userData,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.saveContext(context, request, response);

        log.info("Initialized session for guest user {} with id: {}", displayName, userData.getUserId());
    }
    public void initializeSessionAfterSpotifyLogin(Authentication authentication, HttpServletRequest request, HttpServletResponse response, UserData oldUser) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            log.error("Failed to initialize session after Spotify login: authentication principal is not an OAuth2User");
            return;
        }

        boolean isPremium =  Objects.equals(oauthUser.getAttribute("product"), "premium");

        Set<String> userScopes = oauthUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        boolean hasSpotifyPlayerPermissions = userScopes.containsAll(java.util.Set.of(
                "SCOPE_streaming",
                "SCOPE_user-read-email",
                "SCOPE_user-modify-playback-state",
                "SCOPE_user-read-playback-state"
        ));

        // get profile picture for session
        Object rawImages = oauthUser.getAttribute("images");
        List<SpotifyImage> spotifyImages = mapper.convertValue(
                rawImages,
                new TypeReference<List<SpotifyImage>>() {}
        );

        String imageUrl = spotifyImages.stream()
                .filter(img -> img.getWidth() == 300)
                .map(SpotifyImage::getUrl)
                .findFirst()
                .orElse(spotifyImages.isEmpty() ? "" : spotifyImages.getFirst().getUrl());

        String smallImageUrl = spotifyImages.stream()
                .filter(img -> img.getWidth() == 64)
                .map(SpotifyImage::getUrl)
                .findFirst()
                .orElse(imageUrl);

        // create new user session object
        UserData userData = null;
        if (oldUser != null) {
            userData = new UserData(
                    oldUser.getUserId(),
                    oauthUser.getAttribute("display_name"),
                    oldUser.getPartyId(),
                    true,
                    isPremium,
                    hasSpotifyPlayerPermissions,
                    oauthUser.getName(),
                    imageUrl,
                    smallImageUrl
            );
            userData.setUser(oldUser.isUser());
            userData.setPlayer(oldUser.isPlayer());
            userData.setHost(oldUser.isHost());
            // if user was in party update his profile
            if (oldUser.getPartyId() != null) {
                log.info("User with id {} was in party and logged in via spotify, updating party session with new spotify authenticated profile", userData.getUserId());
                partyService.updateUserProfile(oldUser.getPartyId(), userData);
            }
        } else {
            userData = new UserData(
                    UUID.randomUUID(),
                    oauthUser.getAttribute("display_name"),
                    null,
                    true,
                    isPremium,
                    hasSpotifyPlayerPermissions,
                    oauthUser.getName(),
                    imageUrl,
                    smallImageUrl
            );
        }

        log.info("Initialized session for Spotify user: {}, spotifyId: {} with id: {}", userData.getDisplayName(), userData.getSpotifyId(), userData.getUserId());

        // register new session object
        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                userData,
                authentication.getCredentials(),
                List.of(new SimpleGrantedAuthority("ROLE_SPOTIFY_USER"))
        );

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId(),
                authentication.getName()
        );
        if (client != null) {
            authorizedClientService.saveAuthorizedClient(client, newAuth);
            log.info("Successfully re-mapped Spotify tokens to the new UserData principal");
        } else {
            log.warn("Could not find OAuth2 client for {}, tokens might be lost!", authentication.getName());
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);

        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.saveContext(context, request, response);
    }

    public void logoutSpotify(UserData user, HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (user.getPartyId() != null) {
            partyService.removeUserFromParty(user);
        }
        try {
            String registrationId = user.isHasSpotifyPlayerPermissions() ? "spotify-host" : "spotify";
            authorizedClientService.removeAuthorizedClient(registrationId, authentication.getName());
        } catch (Exception e) {
            // ignore if he doesn't exist
        }

        logoutHandler.logout(request, response, authentication);
    }
}