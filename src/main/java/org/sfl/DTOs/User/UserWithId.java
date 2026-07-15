package org.sfl.DTOs.User;

import java.util.UUID;

public record UserWithId(UUID userId, UserProfile profile) {}
