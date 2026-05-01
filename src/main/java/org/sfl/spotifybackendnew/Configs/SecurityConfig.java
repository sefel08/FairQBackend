package org.sfl.spotifybackendnew.Configs;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.sfl.spotifybackendnew.Services.User.UserSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final UserSessionService userSessionService;

    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository, UserSessionService userSessionService) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.userSessionService = userSessionService;
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)

            .cors(cors -> cors.configurationSource(request -> {
                var config = new CorsConfiguration();
                config.setAllowedOrigins(List.of("http://127.0.0.1:5173"));
                config.setAllowedMethods(List.of("*"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                return config;
            }))

            .authorizeHttpRequests(auth -> auth

                    //logging in and status
                    .requestMatchers("/login/**", "/oauth2/**", "/api/user/login-as-guest", "/api/status").permitAll()
                    //fetching spotify account related data, only for spotify authenticated users
                    .requestMatchers("/api/spotify/user-playlists").hasRole("SPOTIFY_USER")
                    //creating party only for spotify authenticated users
                    .requestMatchers("/api/party/create").hasRole("SPOTIFY_USER")
                    //player endpoints only for spotify authenticated users
                    .requestMatchers("/api/player/**").hasRole("SPOTIFY_USER")

                    .anyRequest().authenticated()
            )

            .oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(sub -> sub
                            .authorizationRequestResolver(new CustomAuthorizationRequestResolver(clientRegistrationRepository))
                    )
                    .successHandler(customSuccessHandler())
                    .failureUrl("http://127.0.0.1:5173")
            )

            .sessionManagement(session -> session
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .expiredUrl("http://127.0.0.1:5173/expired")
            );

        return http.build();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {

            HttpSession session = request.getSession(false);
            UUID oldGuestId = null;
            String oldPartyId = null;

            if (session != null) {
                oldGuestId = (UUID) session.getAttribute("ORIGINAL_GUEST_ID");
                oldPartyId = (String) session.getAttribute("ORIGINAL_PARTY_ID");
                session.removeAttribute("ORIGINAL_GUEST_ID");
                session.removeAttribute("ORIGINAL_PARTY_ID");
            }

            userSessionService.initializeSessionAfterSpotifyLogin(authentication, request, response, oldGuestId, oldPartyId);
            response.sendRedirect("http://127.0.0.1:5173");
        };
    }
}
