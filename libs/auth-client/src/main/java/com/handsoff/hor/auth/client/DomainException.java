package com.handsoff.hor.auth.client;

import java.util.Objects;

public final class DomainException extends RuntimeException {
    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode, String detail) {
        super(Objects.requireNonNull(detail));
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
