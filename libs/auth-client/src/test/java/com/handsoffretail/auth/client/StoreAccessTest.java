package com.handsoffretail.auth.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StoreAccessTest {
    @Test
    void allowsAnAssignedStore() {
        HorPrincipal principal = new HorPrincipal("STORE_USER", 4, "CLIENT_USER", "Name", false, Set.of(8L));

        assertThatCode(() -> StoreAccess.requireStore(principal, 8L)).doesNotThrowAnyException();
    }

    @Test
    void allowsEveryStoreForPlatformAdmin() {
        HorPrincipal principal = new HorPrincipal("ADMIN", 4, "PLATFORM_ADMIN", "Name", true, Set.of());

        assertThatCode(() -> StoreAccess.requireStore(principal, 8L)).doesNotThrowAnyException();
    }

    @Test
    void hidesUnassignedStoreWithNotFound() {
        HorPrincipal principal = new HorPrincipal("STORE_USER", 4, "CLIENT_USER", "Name", false, Set.of());

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> StoreAccess.requireStore(principal, 8L))
                .satisfies(exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.errorCode().code()).isEqualTo("NOT_FOUND");
                    org.assertj.core.api.Assertions.assertThat(exception.errorCode().status()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}
