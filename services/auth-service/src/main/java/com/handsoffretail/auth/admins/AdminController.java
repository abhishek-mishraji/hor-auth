package com.handsoffretail.auth.admins;

import com.handsoffretail.auth.common.PageResult;
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
@RequestMapping("/api/auth/admin/platform-admins")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public PageResult<PlatformAdminResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return adminService.list(status, q, limit, offset);
    }

    @PostMapping
    public ResponseEntity<PlatformAdminResponse> create(
            @Valid @RequestBody CreateAdminRequest request,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.create(request, adminId(authentication)));
    }

    @GetMapping("/{id}")
    public PlatformAdminResponse get(@PathVariable long id) {
        return adminService.get(id);
    }

    @PatchMapping("/{id}")
    public PlatformAdminResponse patch(@PathVariable long id, @Valid @RequestBody PatchAdminRequest request,
            JwtAuthenticationToken authentication) {
        return adminService.patch(id, request, adminId(authentication));
    }

    @PostMapping("/{id}/password-reset")
    public ResponseEntity<Void> resetPassword(@PathVariable long id,
            @Valid @RequestBody PasswordResetRequest request, JwtAuthenticationToken authentication) {
        adminService.resetPassword(id, request.newPassword(), adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable long id, JwtAuthenticationToken authentication) {
        adminService.unlock(id, adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    private long adminId(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getToken().getSubject());
    }
}
