package org.sfl.Controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.sfl.Services.User.UserSessionService;
import org.sfl.DTOs.User.UserData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserSessionController {

    private final UserSessionService userSessionService;

    public UserSessionController(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    @PostMapping("/login-as-guest")
    public ResponseEntity<?> loginAsGuest(
        @RequestBody String displayName, @AuthenticationPrincipal UserData user,
        HttpServletRequest request, HttpServletResponse response
    ) {

        // if user is already authenticated, do nothing
        if (user != null) {
            return ResponseEntity.ok().build();
        }

        if (displayName.length() > 20) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Display name must be less than 20 characters");
        }

        userSessionService.initializeSessionForGuest(request, response, displayName);

        return ResponseEntity.ok().build();
    }
}
