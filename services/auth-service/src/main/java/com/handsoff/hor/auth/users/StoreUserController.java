package com.handsoff.hor.auth.users;

import com.handsoff.hor.auth.common.PageResult;
import jakarta.validation.Valid;
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
@RequestMapping("/api/auth/admin/users")
public class StoreUserController {
    private final StoreUserService storeUserService;

    public StoreUserController(StoreUserService storeUserService) {
        this.storeUserService = storeUserService;
    }

    @GetMapping
    public PageResult<StoreUserResponse> list(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return storeUserService.list(userType, status, q, limit, offset);
    }

    @PostMapping
    public ResponseEntity<StoreUserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeUserService.create(request, adminId(authentication)));
    }

    @GetMapping("/{id}")
    public StoreUserResponse get(@PathVariable long id) {
        return storeUserService.get(id);
    }

    @PatchMapping("/{id}")
    public StoreUserResponse patch(@PathVariable long id, @Valid @RequestBody PatchUserRequest request,
            JwtAuthenticationToken authentication) {
        return storeUserService.patch(id, request, adminId(authentication));
    }

    @PostMapping("/{id}/password-reset")
    public ResponseEntity<Void> resetPassword(@PathVariable long id,
            @Valid @RequestBody UserPasswordResetRequest request, JwtAuthenticationToken authentication) {
        storeUserService.resetPassword(id, request.newPassword(), adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable long id, JwtAuthenticationToken authentication) {
        storeUserService.unlock(id, adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    private long adminId(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getToken().getSubject());
    }
}
