package com.handsoff.hor.auth.session;

import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.AuthErrorCode;

public final class SessionEndedException extends DomainException {
    public SessionEndedException() {
        super(AuthErrorCode.SESSION_ENDED, "The session has ended.");
    }
}
