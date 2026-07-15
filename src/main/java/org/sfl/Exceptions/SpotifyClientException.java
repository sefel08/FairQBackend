package org.sfl.Exceptions;

public class SpotifyClientException extends RuntimeException {
    public SpotifyClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
