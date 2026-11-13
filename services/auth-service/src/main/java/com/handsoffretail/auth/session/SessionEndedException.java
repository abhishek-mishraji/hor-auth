package com.handsoffretail.auth.session;

import com.handsoffretail.auth.client.DomainException;
import com.handsoffretail.auth.common.AuthErrorCode;

public final class SessionEndedException extends DomainException {
    public SessionEndedException() {
        super(AuthErrorCode.SESSION_ENDED, "The session has ended.");
    }
}
