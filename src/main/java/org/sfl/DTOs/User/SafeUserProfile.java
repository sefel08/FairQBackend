package org.sfl.DTOs.User;

public record SafeUserProfile(String displayName, boolean spotifyAuthorized, String profileImageUrl, String smallProfileImageUrl) {}