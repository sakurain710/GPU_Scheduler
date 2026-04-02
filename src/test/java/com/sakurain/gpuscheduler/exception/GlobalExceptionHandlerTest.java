package com.sakurain.gpuscheduler.exception;

import com.sakurain.gpuscheduler.dto.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void handleBusinessException_shouldUseHttpStatusFromException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BusinessException ex = new BusinessException("TASK_FORBIDDEN", "no permission", 403);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getCode());
        assertEquals("no permission", response.getBody().getMessage());
    }
}
