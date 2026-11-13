package com.handsoffretail.auth.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;

public record HorPrincipal(
        String accountType,
        long id,
        String role,
        String name,
        boolean allStores,
        Set<Long> storeIds) {

    public HorPrincipal {
        storeIds = Set.copyOf(storeIds);
    }

    public static HorPrincipal from(Jwt jwt) {
        Object claim = jwt.getClaims().get("stores");
        Set<Long> stores = claim instanceof List<?> values
                ? values.stream().map(HorPrincipal::asLong).collect(Collectors.toCollection(LinkedHashSet::new))
                : Set.of();
        return new HorPrincipal(
                jwt.getClaimAsString("typ"),
                Long.parseLong(jwt.getSubject()),
                jwt.getClaimAsString("role"),
                jwt.getClaimAsString("name"),
                Boolean.TRUE.equals(jwt.getClaim("all_stores")),
                stores);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
