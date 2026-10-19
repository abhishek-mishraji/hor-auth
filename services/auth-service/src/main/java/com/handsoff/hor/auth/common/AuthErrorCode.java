package com.handsoff.hor.auth.common;

import com.handsoff.hor.auth.client.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum AuthErrorCode implements ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    SESSION_ENDED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CSRF_REJECTED(HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    STORE_CODE_TAKEN(HttpStatus.CONFLICT),
    S3_FOLDER_TAKEN(HttpStatus.CONFLICT),
    OWNER_EXISTS(HttpStatus.CONFLICT),
    WRONG_PASSWORD(HttpStatus.UNPROCESSABLE_ENTITY),
    CANNOT_DISABLE_SELF(HttpStatus.UNPROCESSABLE_ENTITY),
    LAST_ACTIVE_ADMIN(HttpStatus.UNPROCESSABLE_ENTITY),
    USER_TYPE_FIXED(HttpStatus.UNPROCESSABLE_ENTITY),
    STORE_CODE_FIXED(HttpStatus.UNPROCESSABLE_ENTITY),
    POS_VENDOR_FIXED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_STORE_ROLE(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_TIMEZONE(HttpStatus.UNPROCESSABLE_ENTITY),
    STATUS_CHANGE_ALONE(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatusCode status;

    AuthErrorCode(HttpStatusCode status) {
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatusCode status() {
        return status;
    }
}
