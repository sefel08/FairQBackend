package org.sfl.Exceptions;

import java.util.UUID;

public class PartyNotFoundException extends RuntimeException {
    public PartyNotFoundException(String partyId) {
        super("Party with id: " + partyId + " does not exist.");
    }
}