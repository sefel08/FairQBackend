package org.sfl.Configs;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.sfl.DTOs.User.UserData;
import org.sfl.Services.User.UserSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${frontend.url}")
    private String frontendUrl;

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
            .requestCache(RequestCacheConfigurer::disable)
            .cors(cors -> cors.configurationSource(request -> {
                var config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(frontendUrl));
                config.setAllowedMethods(List.of("*"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                return config;
            }))
            .authorizeHttpRequests(auth -> auth

                    //logging in and status
                    .requestMatchers("/login/**", "/oauth2/**", "/api/user/login-as-guest", "/api/user/save-return-url", "/api/status").permitAll()
                    //fetching spotify account related data, only for spotify authenticated users
                    .requestMatchers("/api/spotify/user-playlists", "/api/spotify/playlist").hasRole("SPOTIFY_USER")
                    //creating party only for spotify authenticated users
                    .requestMatchers("/api/party/create").hasRole("SPOTIFY_USER")
                    //player endpoints only for spotify authenticated users
                    .requestMatchers("/api/player/**", "/api/spotify-token").hasRole("SPOTIFY_USER")
                    //logout spotify only for spotify authenticated users
                    .requestMatchers("/api/user/spotify/logout").hasRole("SPOTIFY_USER")

                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            PathPatternRequestMatcher.withDefaults().matcher("/api/**")
                    )
            )
            .oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(sub -> sub
                            .authorizationRequestResolver(new CustomAuthorizationRequestResolver(clientRegistrationRepository))
                    )
                    .successHandler(customSuccessHandler())
                    .failureHandler((request, response, exception) -> {
                        log.error("OAuth2 login failed: ", exception);
                        response.sendRedirect(frontendUrl + "?loginError=" + exception.getMessage());
                    })
                    .loginPage("/login")
            )
            .sessionManagement(session -> session
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .expiredUrl(frontendUrl + "/expired")
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
            UserData oldUser = null;

            if (session != null) {
                oldUser = (UserData) session.getAttribute("OLD_USER");
                session.invalidate();
            }

            userSessionService.initializeSessionAfterSpotifyLogin(authentication, request, response, oldUser);
            response.sendRedirect(frontendUrl);
        };
    }
}