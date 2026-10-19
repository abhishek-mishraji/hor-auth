package com.handsoff.hor.auth.assignments;

public record AssignmentUser(long userId, String userType, String fullName, String email, String status) {
}
