package org.sfl.spotifybackendnew.DTOs.User;

import java.util.UUID;

public record UserWithId(UUID userId, UserProfile profile) {}
