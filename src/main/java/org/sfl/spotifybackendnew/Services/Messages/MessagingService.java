package org.sfl.spotifybackendnew.Services.Messages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.DTOs.Party.Message;
import org.sfl.spotifybackendnew.Enums.MessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendUpdate(String partyId, MessageType messageType) {
//        log.info("Sending message: {}", messageType.toString());
        messagingTemplate.convertAndSend("/party/" + partyId, new Message(messageType, null));
    }
    public void sendUpdate(String partyId, Message message) {
//        log.info("Sending message: {} with payload: {}", message.type().toString(), message.payload().toString());
        messagingTemplate.convertAndSend("/party/" + partyId, message);
    }
    public void sendPrivateUpdate(UUID userId, MessageType messageType) {
        log.info("Sending to user {}: {}", userId, messageType);
        messagingTemplate.convertAndSendToUser(userId.toString(), "/messages", new Message(messageType, null));
    }
}