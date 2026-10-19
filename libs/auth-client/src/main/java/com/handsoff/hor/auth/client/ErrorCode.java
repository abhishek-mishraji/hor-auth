package com.handsoff.hor.auth.client;

import org.springframework.http.HttpStatusCode;

public interface ErrorCode {
    String code();

    HttpStatusCode status();
}
