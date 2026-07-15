package org.sfl.Exceptions;

public class SpotifyAuthenticationException extends RuntimeException {
    public SpotifyAuthenticationException(String message) {
        super(message);
    }
}