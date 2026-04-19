package org.sfl.spotifybackendnew.Controllers;

import org.sfl.spotifybackendnew.DTOs.User.UserData;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @GetMapping("/status")
    public Map<String, Object> checkStatus(@AuthenticationPrincipal UserData user) {
        // doesn't have session created
        if (user == null) {
            return Map.of("isLoggedIn", false);
        }

        Map<String, Object> status = new HashMap<>();
        status.put("isLoggedIn", true);
        status.put("isSpotifyAuthenticated", user.isSpotifyAuthenticated());
        status.put("displayName", user.getDisplayName());
        status.put("imageUrl", user.getImageUrl());
        status.put("smallImageUrl", user.getSmallImageUrl());

        return status;
    }
}