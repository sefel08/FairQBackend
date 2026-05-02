package org.sfl.spotifybackendnew.Services.Messages;

import lombok.RequiredArgsConstructor;
import org.sfl.spotifybackendnew.Enums.MessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessagingService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendUpdate(String partyId, MessageType messageType) {
        messagingTemplate.convertAndSend("/party/" + partyId, messageType);
    }
}
