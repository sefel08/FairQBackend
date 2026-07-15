package org.sfl.DTOs.Party;

public record SimpleResponse(
        boolean success,
        String message
) {}