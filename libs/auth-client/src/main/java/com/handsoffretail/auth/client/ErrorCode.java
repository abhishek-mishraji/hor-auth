package com.handsoffretail.auth.client;

import org.springframework.http.HttpStatusCode;

public interface ErrorCode {
    String code();

    HttpStatusCode status();
}
