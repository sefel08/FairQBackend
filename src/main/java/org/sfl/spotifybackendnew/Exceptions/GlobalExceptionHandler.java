package org.sfl.spotifybackendnew.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PartyNotFoundException.class)
    public ResponseEntity<String> handlePartyNotFound(PartyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(SpotifyAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleSpotifyAuth(SpotifyAuthenticationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", ex.getMessage());
        body.put("error", "SPOTIFY_AUTH_ERROR");

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }
}