package com.monglife.discovery.app.auth.account.exception;

import com.monglife.core.exception.ErrorException;
import com.monglife.discovery.app.auth.global.enums.AuthResponse;
import lombok.Getter;

import java.util.Collections;

@Getter
public class TokenExpiredException extends ErrorException {

    public TokenExpiredException(String accessToken) {
        this.response = AuthResponse.DISCOVERY_AUTH_ACCESS_TOKEN_EXPIRED;
        this.result = Collections.singletonMap("accessToken", accessToken);
    }
}