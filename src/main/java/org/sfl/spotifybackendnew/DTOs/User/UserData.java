package org.sfl.spotifybackendnew.DTOs.User;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public class UserData implements UserDetails {

    @Getter private final UUID userId;
    @Getter private final String displayName;
    @Getter @Setter
    private String partyId;

    @Getter
    private final boolean isSpotifyAuthenticated;
    @Getter
    private final boolean isPremium;
    @Getter
    private final boolean hasHostPermissions;
    @Getter @Nullable
    private final String spotifyId;
    @Getter @Nullable
    private final String imageUrl;
    @Getter @Nullable
    private final String smallImageUrl;

    public UserData(UUID userId, String displayName, String partyId, boolean isSpotifyAuthenticated, boolean isPremium, boolean hasHostPermissions, @Nullable String spotifyId, @Nullable String imageUrl, @Nullable String smallImageUrl) {
        this.userId = userId;
        this.displayName = displayName;
        this.partyId = partyId;
        this.isSpotifyAuthenticated = isSpotifyAuthenticated;
        this.isPremium = isPremium;
        this.hasHostPermissions = hasHostPermissions;
        this.spotifyId = spotifyId;
        this.imageUrl = imageUrl;
        this.smallImageUrl = smallImageUrl;
    }

    @Override
    public int hashCode() {
        return spotifyId != null ? Objects.hash(spotifyId) : Objects.hash(userId);
    }

    // necessary overrides for UserDetails
    @Override public String getUsername() { return spotifyId; }
    @Override public String getPassword() { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return null; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}