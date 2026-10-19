package com.handsoff.hor.auth.client;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hor.auth.client")
public class HorAuthClientProperties {
    private String jwksUri = "http://127.0.0.1:8091/api/auth/.well-known/jwks.json";
    private List<String> allowedOrigins = new ArrayList<>();

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>(allowedOrigins);
    }
}
