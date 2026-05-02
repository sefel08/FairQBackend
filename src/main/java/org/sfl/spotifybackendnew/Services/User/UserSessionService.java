package org.sfl.spotifybackendnew.Services.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.sfl.spotifybackendnew.Services.Party.PartyService;
import org.sfl.spotifybackendnew.SpotifyDTOs.SubDTOs.SpotifyImage;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class UserSessionService {

    private final JsonMapper mapper;
    private final PartyService partyService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public UserSessionService(JsonMapper mapper, PartyService partyService, OAuth2AuthorizedClientService authorizedClientService) {
        this.mapper = mapper;
        this.partyService = partyService;
        this.authorizedClientService = authorizedClientService;
    }

    public void initializeSessionForGuest(HttpServletRequest request, HttpServletResponse response, String displayName) {
        HttpSession session = request.getSession(true);

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

        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        log.info("Initialized session for guest user {} with id: {}", displayName, userData.getUserId());
    }
    public void initializeSessionAfterSpotifyLogin(Authentication authentication, HttpServletRequest request, HttpServletResponse response, UUID oldUserId, String oldPartyId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            log.error("Failed to initialize session after Spotify login: authentication principal is not an OAuth2User");
            return;
        }

        boolean isPremium =  Objects.equals(oauthUser.getAttribute("product"), "premium");

        boolean hasHostPermissions = oauthUser.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "SCOPE_streaming"));

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
        UserData userData = new UserData(
                (oldUserId == null) ? UUID.randomUUID() : oldUserId,
                oauthUser.getAttribute("display_name"),
                oldPartyId,
                true,
                isPremium,
                hasHostPermissions,
                oauthUser.getName(),
                imageUrl,
                smallImageUrl
        );

        log.info("Initialized session for Spotify user: {}, spotifyId: {} with id: {}", userData.getDisplayName(), userData.getSpotifyId(), userData.getUserId());

        // if user was in party update his profile
        if (oldUserId != null && oldPartyId != null) {
            log.info("User with id {} was in party and logged in via spotify, updating party session with new spotify authenticated profile", userData.getUserId());
            partyService.updateUserProfile(oldPartyId, userData);
        }

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
}