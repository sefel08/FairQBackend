package org.sfl.DTOs.Party;

import org.sfl.Enums.MessageType;

public record Message(MessageType type, Object payload) {}
