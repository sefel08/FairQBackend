package org.sfl.spotifybackendnew.Services.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    public UserSessionService(JsonMapper mapper) {
        this.mapper = mapper;
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

        log.info("Initialized session for guest user {} with id: {}", displayName, userData.getUserId());

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
    }
    public void initializeSessionAfterSpotifyLogin(Authentication authentication, HttpServletRequest request, HttpServletResponse response, UUID oldUserId, String oldPartyId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            log.error("Failed to initialize session after Spotify login: authentication principal is not an OAuth2User");
            return;
        }

        boolean isPremium =  oauthUser.getAttribute("product") != null && Objects.equals(oauthUser.getAttribute("product"), "premium");

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

        // register new session object
        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                userData,
                authentication.getCredentials(),
                List.of(new SimpleGrantedAuthority("ROLE_SPOTIFY_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);

        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.saveContext(context, request, response);
    }
}