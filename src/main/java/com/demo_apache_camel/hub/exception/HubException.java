package com.demo_apache_camel.hub.exception;

import lombok.Getter;

@Getter
public class HubException extends RuntimeException {

    private final String errorCode;
    private final String status;

    public HubException(String errorCode, String status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public HubException(String errorCode, String status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }
}
