package com.handsoff.hor.auth.client;

import java.util.Map;
import java.util.Objects;

public class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public DomainException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    public DomainException(ErrorCode errorCode, String detail, Map<String, Object> properties) {
        super(Objects.requireNonNull(detail));
        this.errorCode = Objects.requireNonNull(errorCode);
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
