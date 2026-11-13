package com.handsoffretail.auth.common;

import com.handsoffretail.auth.client.ErrorCode;
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
    WRONG_PASSWORD(unprocessableEntity()),
    CANNOT_DISABLE_SELF(unprocessableEntity()),
    LAST_ACTIVE_ADMIN(unprocessableEntity()),
    USER_TYPE_FIXED(unprocessableEntity()),
    STORE_CODE_FIXED(unprocessableEntity()),
    POS_VENDOR_FIXED(unprocessableEntity()),
    INVALID_STORE_ROLE(unprocessableEntity()),
    INVALID_TIMEZONE(unprocessableEntity()),
    STATUS_CHANGE_ALONE(unprocessableEntity()),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatusCode status;

    private static HttpStatusCode unprocessableEntity() {
        return HttpStatusCode.valueOf(422);
    }

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
