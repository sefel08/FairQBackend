package org.sfl.spotifybackendnew.Exceptions;

public class SpotifyAuthenticationException extends RuntimeException {
    public SpotifyAuthenticationException(String message) {
        super(message);
    }
}