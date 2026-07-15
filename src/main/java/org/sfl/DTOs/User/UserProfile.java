package org.sfl.DTOs.User;

public record UserProfile(String displayName, boolean spotifyAuthorized, String spotifyId, String profileImageUrl, String smallProfileImageUrl) {}