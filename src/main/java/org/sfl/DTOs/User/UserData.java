package org.sfl.DTOs.User;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public class UserData implements UserDetails {

    @Getter
    private final UUID userId;
    @Getter
    private final String displayName;
    @Getter @Setter
    private String partyId;

    @Getter
    private final boolean isSpotifyAuthenticated;
    @Getter
    private final boolean isPremium;
    @Getter
    private final boolean hasSpotifyPlayerPermissions;
    @Getter @Nullable
    private final String spotifyId;
    @Getter @Nullable
    private final String imageUrl;
    @Getter @Nullable
    private final String smallImageUrl;

    // device views options (player, user, host)
    @Getter @Setter
    private boolean isPlayer = false;
    @Getter @Setter
    private boolean isUser = false;
    @Getter @Setter
    private boolean isHost = false;

    public UserData(
            UUID userId,
            String displayName,
            String partyId,
            boolean isSpotifyAuthenticated,
            boolean isPremium,
            boolean hasSpotifyPlayerPermissions,
            @Nullable String spotifyId,
            @Nullable String imageUrl,
            @Nullable String smallImageUrl
    ) {
        this.userId = userId;
        this.displayName = displayName;
        this.partyId = partyId;
        this.isSpotifyAuthenticated = isSpotifyAuthenticated;
        this.isPremium = isPremium;
        this.hasSpotifyPlayerPermissions = hasSpotifyPlayerPermissions;
        this.spotifyId = spotifyId;
        this.imageUrl = imageUrl;
        this.smallImageUrl = smallImageUrl;
    }
    //deep copy constructor
    public UserData(UserData other) {
        this.userId = other.userId;
        this.displayName = other.displayName;
        this.partyId = other.partyId;
        this.isSpotifyAuthenticated = other.isSpotifyAuthenticated;
        this.isPremium = other.isPremium;
        this.hasSpotifyPlayerPermissions = other.hasSpotifyPlayerPermissions;
        this.spotifyId = other.spotifyId;
        this.imageUrl = other.imageUrl;
        this.smallImageUrl = other.smallImageUrl;
        this.isPlayer = other.isPlayer;
        this.isUser = other.isUser;
        this.isHost = other.isHost;
    }

    public void clearRoles() {
        isPlayer = false;
        isUser = false;
        isHost = false;
    }

    @Override
    public int hashCode() {
        return isPlayer ? Objects.hash(spotifyId) : Objects.hash(userId);
    }

    // necessary overrides for UserDetails
    @Override @NullMarked
    public String getUsername() { return userId.toString(); }
    @Override public String getPassword() { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return null; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}