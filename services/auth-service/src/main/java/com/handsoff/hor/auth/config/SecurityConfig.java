package com.handsoff.hor.auth.config;

import com.handsoff.hor.auth.client.AuthCookieTokenResolver;
import com.handsoff.hor.auth.client.HorHttpSecurity;
import com.handsoff.hor.auth.client.HorJwtDecoderConfiguration;
import com.handsoff.hor.auth.client.ProblemResponses;
import com.handsoff.hor.auth.token.SigningKeyStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import com.handsoff.hor.auth.client.CsrfHeaderFilter;
import com.handsoff.hor.auth.client.RequestLoggingFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    JwtDecoder localJwtDecoder(SigningKeyStore keyStore, Clock clock) {
        return HorJwtDecoderConfiguration.create(keyStore.publicKey(), clock);
    }

    @Bean
    SecurityFilterChain authSecurityFilterChain(HttpSecurity http, AuthCookieTokenResolver tokenResolver,
            CsrfHeaderFilter csrfHeaderFilter, RequestLoggingFilter requestLoggingFilter, JwtDecoder jwtDecoder)
            throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/.well-known/jwks.json").permitAll()
                        .requestMatchers("/api/auth/admin/**").hasAuthority("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                                        .authenticationEntryPoint(ProblemResponses.authenticationEntryPoint())
                                        .bearerTokenResolver(request -> isPublicSessionPath(request.getRequestURI())
                                                ? null : tokenResolver.resolve(request))
                                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(HorHttpSecurity.jwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(ProblemResponses.authenticationEntryPoint())
                        .accessDeniedHandler(ProblemResponses.accessDeniedHandler()))
                .addFilterAfter(requestLoggingFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(csrfHeaderFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private boolean isPublicSessionPath(String path) {
        return path.equals("/api/auth/login") || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout") || path.equals("/api/auth/.well-known/jwks.json");
    }
}
