package com.handsoffretail.auth.stores;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.handsoffretail.auth.client.DomainException;
import com.handsoffretail.auth.client.ProblemDetailsAdvice;
import com.handsoffretail.auth.common.AuthErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreControllerTest {

    @Mock
    private StoreService storeService;

    @InjectMocks
    private StoreController storeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storeController)
                .setControllerAdvice(new ProblemDetailsAdvice())
                .build();
    }

    @Test
    void syncStores_withValidVendor_returns200AndStores() throws Exception {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        StoreResponse store1 = new StoreResponse(1L, "ST_101", "Alpha Store", "VERIFONE",
                "verifone/ST_101/", "100 Main St", null, "Rockville", "IN", "47872",
                "US", null, "America/Indiana/Indianapolis", "ACTIVE", "John Doe",
                2L, now, now);
        StoreResponse store2 = new StoreResponse(2L, "ST_102", "Beta Store", "VERIFONE",
                "verifone/ST_102/", "200 Oak St", null, "Rockville", "IN", "47872",
                "US", null, "America/Indiana/Indianapolis", "INACTIVE", "Jane Smith",
                1L, now, now);

        when(storeService.listByPosVendor("verifone")).thenReturn(List.of(store1, store2));

        mockMvc.perform(get("/api/auth/admin/stores/sync/verifone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].storeCode", is("ST_101")))
                .andExpect(jsonPath("$[0].storeName", is("Alpha Store")))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")))
                .andExpect(jsonPath("$[1].storeCode", is("ST_102")))
                .andExpect(jsonPath("$[1].storeName", is("Beta Store")))
                .andExpect(jsonPath("$[1].status", is("INACTIVE")));

        verify(storeService).listByPosVendor("verifone");
    }

    @Test
    void syncStores_withUnknownVendor_returns400ValidationFailed() throws Exception {
        when(storeService.listByPosVendor("unknown"))
                .thenThrow(new DomainException(AuthErrorCode.VALIDATION_FAILED,
                        "invalid pos vendor: unknown. Allowed values: [VERIFONE, GILBARCO]"));

        mockMvc.perform(get("/api/auth/admin/stores/sync/unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.detail", is("invalid pos vendor: unknown. Allowed values: [VERIFONE, GILBARCO]")));

        verify(storeService).listByPosVendor("unknown");
    }
}
