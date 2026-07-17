package org.sfl.Services.User;

import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.User.UserData;
import org.sfl.Services.Messages.MessagingService;
import org.sfl.Services.Party.PartyService;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SessionComponent {

    private final PartyService partyService;
    private final MessagingService messagingService;

    public SessionComponent(PartyService partyService, MessagingService messagingService) {
        this.partyService = partyService;
        this.messagingService = messagingService;
    }

    @EventListener
    public void handleSessionTermination(HttpSessionDestroyedEvent event) {
        for (SecurityContext securityContext : event.getSecurityContexts()) {
            Authentication auth = securityContext.getAuthentication();

            if (auth != null && auth.getPrincipal() instanceof UserData user) {
                log.info("Session of user {} timed out. Removing from party: {}", user.getDisplayName(), user.getPartyId() != null ? user.getPartyId() : "None");
                if (user.getPartyId() != null)
                    partyService.removeUserFromParty(user);
            }
        }
    }
}
