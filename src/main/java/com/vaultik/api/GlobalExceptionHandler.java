package com.vaultik.api;

import com.vaultik.storage.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ErrorResponse> storage(StorageException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
