package com.handsoffretail.auth.token;

import com.handsoffretail.auth.account.Account;
import java.time.Instant;

public interface AccessTokenIssuer {
    String issue(Account account, Instant issuedAt);
}
