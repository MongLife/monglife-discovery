package com.monglife.discovery.auth.app.controller;

import com.monglife.core.enums.error.ErrorCode;
import com.monglife.core.enums.error.GlobalErrorCode;
import com.monglife.core.dto.res.ErrorResDto;
import com.monglife.core.exception.ErrorException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthControllerAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> validatedExceptionHandler() {
        ErrorCode errorCode = GlobalErrorCode.INVALID_PARAMETER;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResDto.of(errorCode));
    }

    @ExceptionHandler(ErrorException.class)
    public ResponseEntity<Object> errorExceptionHandler(ErrorException e) {
        ErrorCode errorCode = e.errorCode;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResDto.of(errorCode));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> runtimeExceptionHandler() {
        ErrorCode errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResDto.of(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> exceptionHandler() {
        ErrorCode appErrorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(appErrorCode.getHttpStatus()).body(ErrorResDto.of(appErrorCode));
    }
}