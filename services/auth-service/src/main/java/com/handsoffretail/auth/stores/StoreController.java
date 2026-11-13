package com.handsoffretail.auth.stores;

import com.handsoffretail.auth.common.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/admin/stores")
public class StoreController {
    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public PageResult<StoreResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String posVendor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return storeService.list(status, posVendor, q, limit, offset);
    }

    @GetMapping("/sync/{posVendor}")
    public List<StoreResponse> syncStores(@PathVariable String posVendor) {
        return storeService.listByPosVendor(posVendor);
    }

    @PostMapping
    public ResponseEntity<StoreResponse> create(
            @Valid @RequestBody CreateStoreRequest request,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeService.create(request, adminId(authentication)));
    }

    @GetMapping("/{id}")
    public StoreResponse get(@PathVariable long id) {
        return storeService.get(id);
    }

    @PatchMapping("/{id}")
    public StoreResponse patch(@PathVariable long id, @Valid @RequestBody PatchStoreRequest request,
            JwtAuthenticationToken authentication) {
        return storeService.patch(id, request, adminId(authentication));
    }

    private long adminId(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getToken().getSubject());
    }
}
