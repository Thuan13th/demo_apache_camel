package com.demo_apache_camel.hub.exception;

import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HubException.class)
    public ResponseEntity<OrderResponse> handleHubException(HubException ex) {
        log.error("[GLOBAL-EXCEPTION] HubException [Code: {}, Status: {}]: {}", 
                ex.getErrorCode(), ex.getStatus(), ex.getMessage());

        HttpStatus status = "REJECTED".equalsIgnoreCase(ex.getStatus())
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.BAD_GATEWAY;

        OrderResponse response = OrderResponse.builder()
                .orderId("N/A")
                .status(ex.getStatus())
                .serviceType("N/A")
                .finalAmount(0.0)
                .message(ex.getMessage())
                .processedBy("HUB_EXCEPTION_HANDLER")
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OrderResponse> handleGenericException(Exception ex) {
        log.error("[GLOBAL-EXCEPTION] Unhandled Exception: ", ex);

        OrderResponse response = OrderResponse.builder()
                .orderId("N/A")
                .status("FAILED")
                .serviceType("N/A")
                .finalAmount(0.0)
                .message("Lỗi xử lý nội bộ tại Hub: " + ex.getMessage())
                .processedBy("HUB_GLOBAL_SAFETY_NET")
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
