package com.handsoff.hor.auth.token;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class JwksController {
    private final SigningKeyStore keyStore;

    public JwksController(SigningKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<JwksResponse> jwks() {
        Map<String, Object> key = keyStore.publicJwk().toJSONObject();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(3600, java.util.concurrent.TimeUnit.SECONDS).cachePublic())
                .body(new JwksResponse(List.of(key)));
    }

    public record JwksResponse(List<Map<String, Object>> keys) {
        public JwksResponse {
            keys = List.copyOf(keys);
        }
    }
}
