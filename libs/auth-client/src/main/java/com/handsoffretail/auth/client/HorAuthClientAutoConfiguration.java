package com.handsoffretail.auth.client;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@AutoConfiguration
@EnableConfigurationProperties(HorAuthClientProperties.class)
@Import(ProblemDetailsAdvice.class)
public class HorAuthClientAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    Clock horAuthClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(BearerTokenResolver.class)
    AuthCookieTokenResolver authCookieTokenResolver() {
        return new AuthCookieTokenResolver();
    }

    @Bean
    @ConditionalOnMissingBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)
    org.springframework.security.oauth2.jwt.JwtDecoder horJwtDecoder(HorAuthClientProperties properties, Clock clock) {
        return HorJwtDecoderConfiguration.create(properties.getJwksUri(), clock);
    }

    @Bean
    CsrfHeaderFilter csrfHeaderFilter(HorAuthClientProperties properties) {
        return new CsrfHeaderFilter(properties);
    }

    @Bean
    FilterRegistrationBean<CsrfHeaderFilter> csrfHeaderFilterRegistration(CsrfHeaderFilter filter) {
        FilterRegistrationBean<CsrfHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(RequestLoggingFilter filter) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain horSecurityFilterChain(
            HttpSecurity http,
            BearerTokenResolver tokenResolver,
            CsrfHeaderFilter csrfHeaderFilter,
            RequestLoggingFilter requestLoggingFilter) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                                        .authenticationEntryPoint(ProblemResponses.authenticationEntryPoint())
                                        .bearerTokenResolver(tokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(HorHttpSecurity.jwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(ProblemResponses.authenticationEntryPoint())
                        .accessDeniedHandler(ProblemResponses.accessDeniedHandler()))
                .addFilterAfter(requestLoggingFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(csrfHeaderFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
