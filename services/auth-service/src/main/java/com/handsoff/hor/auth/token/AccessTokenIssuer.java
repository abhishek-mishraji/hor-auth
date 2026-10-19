package com.handsoff.hor.auth.token;

import com.handsoff.hor.auth.account.Account;
import java.time.Instant;

public interface AccessTokenIssuer {
    String issue(Account account, Instant issuedAt);
}
