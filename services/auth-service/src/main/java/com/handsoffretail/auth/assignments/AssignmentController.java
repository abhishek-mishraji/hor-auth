package com.handsoffretail.auth.assignments;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/admin/stores/{storeId}/members")
public class AssignmentController {
    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<StoreMemberResponse> members(@PathVariable long storeId) {
        return assignmentService.members(storeId);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<StoreMemberResponse> assign(@PathVariable long storeId, @PathVariable long userId,
            @Valid @RequestBody AssignMemberRequest request, JwtAuthenticationToken authentication) {
        AssignmentResult result = assignmentService.assign(storeId, userId, request.storeRole(), adminId(authentication));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.member());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unassign(@PathVariable long storeId, @PathVariable long userId,
            JwtAuthenticationToken authentication) {
        assignmentService.unassign(storeId, userId, adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    private long adminId(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getToken().getSubject());
    }
}
