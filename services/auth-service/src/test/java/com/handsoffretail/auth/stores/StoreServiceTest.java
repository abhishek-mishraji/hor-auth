package com.handsoffretail.auth.stores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.handsoffretail.auth.audit.AuditWriter;
import com.handsoffretail.auth.client.DomainException;
import com.handsoffretail.auth.common.AuthErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository repository;

    @Mock
    private StoreRules rules;

    @Mock
    private AuditWriter auditWriter;

    private Clock clock;
    private StoreService storeService;

    @BeforeEach
    void setUp() {
        clock = Clock.systemUTC();
        storeService = new StoreService(repository, rules, auditWriter, clock);
    }

    @Test
    void listByPosVendor_normalizesAndReturnsStores() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        StoreView view1 = new StoreView(1L, "ST_101", "Alpha Store", "VERIFONE",
                "verifone/ST_101/", "100 Main St", null, "Rockville", "IN", "47872",
                "US", null, "America/Indiana/Indianapolis", "ACTIVE", "John Doe",
                2L, now, now);
        StoreView view2 = new StoreView(2L, "ST_102", "Beta Store", "VERIFONE",
                "verifone/ST_102/", "200 Oak St", null, "Rockville", "IN", "47872",
                "US", null, "America/Indiana/Indianapolis", "INACTIVE", null,
                0L, now, now);

        when(repository.findByPosVendor("VERIFONE")).thenReturn(List.of(view1, view2));

        List<StoreResponse> responses = storeService.listByPosVendor("  verifone  ");

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).storeCode()).isEqualTo("ST_101");
        assertThat(responses.get(0).status()).isEqualTo("ACTIVE");
        assertThat(responses.get(1).storeCode()).isEqualTo("ST_102");
        assertThat(responses.get(1).status()).isEqualTo("INACTIVE");

        verify(repository).findByPosVendor("VERIFONE");
    }

    @Test
    void listByPosVendor_nullOrBlank_throwsValidationFailed() {
        assertThatThrownBy(() -> storeService.listByPosVendor(null))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException de = (DomainException) e;
                    assertThat(de.errorCode()).isEqualTo(AuthErrorCode.VALIDATION_FAILED);
                    assertThat(de.getMessage()).isEqualTo("posVendor is required");
                });

        assertThatThrownBy(() -> storeService.listByPosVendor("   "))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException de = (DomainException) e;
                    assertThat(de.errorCode()).isEqualTo(AuthErrorCode.VALIDATION_FAILED);
                    assertThat(de.getMessage()).isEqualTo("posVendor is required");
                });
    }

    @Test
    void listByPosVendor_unknownVendor_throwsValidationFailed() {
        assertThatThrownBy(() -> storeService.listByPosVendor("UNKNOWN"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException de = (DomainException) e;
                    assertThat(de.errorCode()).isEqualTo(AuthErrorCode.VALIDATION_FAILED);
                    assertThat(de.getMessage()).contains("invalid pos vendor: UNKNOWN");
                });
    }
}
