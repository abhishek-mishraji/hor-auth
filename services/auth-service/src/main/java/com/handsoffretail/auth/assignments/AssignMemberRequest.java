package com.handsoffretail.auth.assignments;

import jakarta.validation.constraints.NotBlank;

public record AssignMemberRequest(@NotBlank String storeRole) {
}
