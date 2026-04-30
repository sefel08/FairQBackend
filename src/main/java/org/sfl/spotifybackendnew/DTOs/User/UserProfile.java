package org.sfl.spotifybackendnew.DTOs.User;

public record UserProfile(String displayName, boolean spotifyAuthorized, String spotifyId, String profileImageUrl, String smallProfileImageUrl) {}