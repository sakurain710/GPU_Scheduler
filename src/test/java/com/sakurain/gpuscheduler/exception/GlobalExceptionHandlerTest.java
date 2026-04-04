package com.sakurain.gpuscheduler.exception;

import com.sakurain.gpuscheduler.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusinessException_shouldUseHttpStatusFromException() {
        BusinessException ex = new BusinessException("TASK_FORBIDDEN", "no permission", 403);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getCode());
        assertEquals("TASK_FORBIDDEN", response.getBody().getErrorCode());
        assertEquals("no permission", response.getBody().getMessage());
    }

    @Test
    void handleAuthenticationException_shouldReturnAuthBadCredentials() {
        Result<Void> result = handler.handleAuthenticationException(new BadCredentialsException("bad credentials"));
        assertEquals(401, result.getCode());
        assertEquals("AUTH_BAD_CREDENTIALS", result.getErrorCode());
    }

    @Test
    void handleAuthenticationException_shouldReturnCredentialMissing() {
        Result<Void> result = handler.handleAuthenticationException(
                new InsufficientAuthenticationException("missing token"));
        assertEquals(401, result.getCode());
        assertEquals("AUTH_CREDENTIAL_MISSING", result.getErrorCode());
    }

    @Test
    void handleAccessDeniedException_shouldReturnAuthAccessDenied() {
        Result<Void> result = handler.handleAccessDeniedException(new AccessDeniedException("forbidden"));
        assertEquals(403, result.getCode());
        assertEquals("AUTH_ACCESS_DENIED", result.getErrorCode());
    }
}
